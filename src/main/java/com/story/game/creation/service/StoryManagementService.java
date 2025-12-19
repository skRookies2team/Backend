package com.story.game.creation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.creation.dto.*;
import com.story.game.common.dto.*;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.common.entity.StoryData;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.infrastructure.s3.S3Service;
import com.story.game.rag.dto.CharacterIndexRequestDto;
import com.story.game.rag.dto.NovelIndexRequestDto;
import com.story.game.rag.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryManagementService {

    private final StoryCreationRepository storyCreationRepository;
    private final RagService ragService;
    private final StoryDataRepository storyDataRepository;
    private final WebClient relayServerWebClient;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

    @org.springframework.beans.factory.annotation.Value("${aws.s3.bucket}")
    private String bucketName;

    @Transactional
    public StoryUploadResponseDto uploadNovel(StoryUploadRequestDto request) {
        log.info("=== Upload Novel ===");
        log.info("Title: {}", request.getTitle());

        String storyId = "story_" + UUID.randomUUID().toString().substring(0, 8);

        StoryCreation storyCreation = StoryCreation.builder()
                .id(storyId)
                .title(request.getTitle())
                .genre(request.getGenre())
                .novelText(request.getNovelText())
                .status(StoryCreation.CreationStatus.ANALYZING)
                .currentPhase("ANALYZING")
                .progressPercentage(0)
                .progressMessage("Starting novel analysis...")
                .build();

        storyCreation = storyCreationRepository.save(storyCreation);

        startAnalysisAsync(storyId, request.getNovelText());

        return StoryUploadResponseDto.builder()
                .storyId(storyCreation.getId())
                .title(storyCreation.getTitle())
                .genre(storyCreation.getGenre())
                .status(storyCreation.getStatus())
                .createdAt(storyCreation.getCreatedAt())
                .build();
    }

    @Transactional
    public void startAnalysisAsync(String storyId, String novelText) {
        try {
            log.info("Starting AI analysis for story: {}", storyId);
            log.info("Novel text parameter - is null: {}, length: {}",
                novelText == null,
                novelText != null ? novelText.length() : 0);

            // Upload novel text to S3 for RAG access
            String novelFileKey = "novels/original/" + storyId + ".txt";
            s3Service.uploadFile(novelFileKey, novelText);
            log.info("Uploaded original novel to S3: {}", novelFileKey);

            // Generate pre-signed download URL for RAG server
            String novelDownloadUrl = s3Service.generatePresignedDownloadUrl(novelFileKey);
            log.info("Generated Pre-signed download URL for RAG server: {}", novelFileKey);

            // Index novel to RAG server (병렬 처리 - 실패해도 분석 계속 진행)
            StoryCreation storyCreationForRag = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story not found"));
            NovelIndexRequestDto ragRequest = NovelIndexRequestDto.builder()
                    .storyId(storyId)
                    .title(storyCreationForRag.getTitle())
                    .fileKey(novelFileKey)
                    .bucket(bucketName)
                    .build();
            ragService.indexNovel(ragRequest);

            NovelAnalysisRequestDto request = NovelAnalysisRequestDto.builder()
                    .novelText(novelText)
                    .fileKey(novelFileKey)  // S3 파일 키도 함께 전달
                    .bucket(bucketName)
                    .novelDownloadUrl(novelDownloadUrl)  // RAG가 원본 소설을 다운로드할 URL
                    .build();

            log.info("Created NovelAnalysisRequestDto - novelText field is null: {}, length: {}",
                request.getNovelText() == null,
                request.getNovelText() != null ? request.getNovelText().length() : 0);

            try {
                String requestJson = objectMapper.writeValueAsString(request);
                log.info("Serialized JSON to send to relay-server: {}",
                    requestJson.length() > 500 ? requestJson.substring(0, 500) + "..." : requestJson);
            } catch (Exception e) {
                log.warn("Failed to serialize request for logging", e);
            }

            NovelAnalysisResponseDto response = relayServerWebClient.post()
                    .uri("/ai/analyze")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NovelAnalysisResponseDto.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("No response from AI server");
            }

            StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story not found"));

            storyCreation.setSummary(response.getSummary());
            storyCreation.setCharactersJson(objectMapper.writeValueAsString(response.getCharacters()));
            storyCreation.setGaugesJson(objectMapper.writeValueAsString(response.getGauges()));
            storyCreation.setS3FileKey(novelFileKey);  // Save original novel S3 key
            // Note: finalEndings will be generated after user selects gauges (in selectGauges method)
            storyCreation.setStatus(StoryCreation.CreationStatus.GAUGES_READY);
            storyCreation.setCurrentPhase("GAUGES_READY");
            storyCreation.setProgressPercentage(30);
            storyCreation.setProgressMessage("Novel analysis completed");

            storyCreationRepository.save(storyCreation);

            log.info("AI analysis completed for story: {}", storyId);
        } catch (Exception e) {
            log.error("Failed to analyze novel for story: {}", storyId, e);
            updateStoryStatus(storyId, StoryCreation.CreationStatus.FAILED, "Analysis failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StorySummaryResponseDto getSummary(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        String summary = null;

        if (storyCreation.getAnalysisResultFileKey() != null && !storyCreation.getAnalysisResultFileKey().isBlank()) {
            try {
                String analysisJson = s3Service.downloadFileContent(storyCreation.getAnalysisResultFileKey());
                NovelAnalysisResponseDto analysisData = objectMapper.readValue(analysisJson, NovelAnalysisResponseDto.class);
                summary = analysisData.getSummary();
            } catch (Exception e) {
                log.error("Failed to download analysis result from S3: {}", storyCreation.getAnalysisResultFileKey(), e);
            }
        }
        else {
            summary = storyCreation.getSummary();
        }

        return StorySummaryResponseDto.builder()
                .storyId(storyCreation.getId())
                .status(storyCreation.getStatus())
                .summary(summary)
                .build();
    }

    @Transactional(readOnly = true)
    public StoryCharactersResponseDto getCharacters(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        List<CharacterDto> characters = null;

        if (storyCreation.getAnalysisResultFileKey() != null && !storyCreation.getAnalysisResultFileKey().isBlank()) {
            try {
                String analysisJson = s3Service.downloadFileContent(storyCreation.getAnalysisResultFileKey());
                NovelAnalysisResponseDto analysisData = objectMapper.readValue(analysisJson, NovelAnalysisResponseDto.class);
                characters = analysisData.getCharacters();
            } catch (Exception e) {
                log.error("Failed to download analysis result from S3: {}", storyCreation.getAnalysisResultFileKey(), e);
            }
        }
        else if (storyCreation.getCharactersJson() != null) {
            try {
                characters = objectMapper.readValue(
                        storyCreation.getCharactersJson(),
                        new TypeReference<List<CharacterDto>>() {}
                );
            } catch (Exception e) {
                log.error("Failed to parse characters JSON", e);
            }
        }

        return StoryCharactersResponseDto.builder()
                .storyId(storyCreation.getId())
                .status(storyCreation.getStatus())
                .characters(characters)
                .build();
    }

    @Transactional(readOnly = true)
    public StoryGaugesResponseDto getGauges(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        List<GaugeDto> gauges = null;

        if (storyCreation.getAnalysisResultFileKey() != null && !storyCreation.getAnalysisResultFileKey().isBlank()) {
            try {
                String analysisJson = s3Service.downloadFileContent(storyCreation.getAnalysisResultFileKey());
                NovelAnalysisResponseDto analysisData = objectMapper.readValue(analysisJson, NovelAnalysisResponseDto.class);
                gauges = analysisData.getGauges();
            } catch (Exception e) {
                log.error("Failed to download analysis result from S3: {}", storyCreation.getAnalysisResultFileKey(), e);
            }
        }
        else if (storyCreation.getGaugesJson() != null) {
            try {
                gauges = objectMapper.readValue(
                        storyCreation.getGaugesJson(),
                        new TypeReference<List<GaugeDto>>() {}
                );
            } catch (Exception e) {
                log.error("Failed to parse gauges JSON", e);
            }
        }

        return StoryGaugesResponseDto.builder()
                .storyId(storyCreation.getId())
                .status(storyCreation.getStatus())
                .gauges(gauges)
                .build();
    }

    @Transactional
    public GaugeSelectionResponseDto selectGauges(String storyId, GaugeSelectionRequestDto request) {
        log.info("=== Select Gauges ===");
        log.info("StoryId: {}, Selected: {}", storyId, request.getSelectedGaugeIds());

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        if (storyCreation.getStatus() != StoryCreation.CreationStatus.GAUGES_READY) {
            throw new IllegalStateException("Cannot select gauges: current status is " + storyCreation.getStatus());
        }

        try {
            storyCreation.setSelectedGaugeIdsJson(
                    objectMapper.writeValueAsString(request.getSelectedGaugeIds())
            );
            storyCreation.setStatus(StoryCreation.CreationStatus.GAUGES_SELECTED);
            storyCreation.setCurrentPhase("GAUGES_SELECTED");
            storyCreation.setProgressPercentage(40);
            storyCreation.setProgressMessage("Gauges selected");

            storyCreation = storyCreationRepository.save(storyCreation);

            List<GaugeDto> allGauges = null;

            if (storyCreation.getAnalysisResultFileKey() != null && !storyCreation.getAnalysisResultFileKey().isBlank()) {
                String analysisJson = s3Service.downloadFileContent(storyCreation.getAnalysisResultFileKey());
                NovelAnalysisResponseDto analysisData = objectMapper.readValue(analysisJson, NovelAnalysisResponseDto.class);
                allGauges = analysisData.getGauges();
            }
            else if (storyCreation.getGaugesJson() != null) {
                allGauges = objectMapper.readValue(
                        storyCreation.getGaugesJson(),
                        new TypeReference<List<GaugeDto>>() {}
                );
            }

            if (allGauges == null) {
                throw new RuntimeException("No gauges found");
            }

            List<GaugeDto> selectedGauges = allGauges.stream()
                    .filter(g -> request.getSelectedGaugeIds().contains(g.getId()))
                    .toList();

            // ✅ Now call /finalize-analysis to generate final endings based on selected gauges
            try {
                log.info("Calling /ai/finalize-analysis with {} selected gauges", selectedGauges.size());

                Map<String, Object> finalizeRequest = Map.of(
                        "novel_summary", storyCreation.getSummary(),
                        "selected_gauges", selectedGauges
                );

                @SuppressWarnings("unchecked")
                Map<String, Object> finalizeResponse = relayServerWebClient.post()
                        .uri("/ai/finalize-analysis")
                        .bodyValue(finalizeRequest)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                if (finalizeResponse != null && finalizeResponse.containsKey("finalEndings")) {
                    storyCreation.setEndingConfigJson(
                            objectMapper.writeValueAsString(finalizeResponse.get("finalEndings"))
                    );
                    storyCreationRepository.save(storyCreation);
                    log.info("Final endings generated and saved successfully");
                } else {
                    log.warn("No finalEndings in finalize-analysis response");
                }
            } catch (Exception e) {
                log.error("Failed to finalize analysis (generate final endings)", e);
                // Don't fail the whole request - just log the error
                // The system can still proceed without finalEndings
            }

            return GaugeSelectionResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .status(storyCreation.getStatus())
                    .selectedGauges(selectedGauges)
                    .build();

        } catch (Exception e) {
            log.error("Failed to select gauges", e);
            throw new RuntimeException("Failed to select gauges: " + e.getMessage());
        }
    }

    @Transactional
    public void selectAndIndexCharacters(String storyId, SelectCharactersRequestDto request) {
        log.info("=== Select and Index Characters ===");
        log.info("StoryId: {}, Selected characters: {}", storyId, request.getCharacterNames());

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        // Validate story state - only allow character selection at appropriate stages
        StoryCreation.CreationStatus status = storyCreation.getStatus();
        if (status != StoryCreation.CreationStatus.CHARACTERS_READY &&
            status != StoryCreation.CreationStatus.GAUGES_READY &&
            status != StoryCreation.CreationStatus.GAUGES_SELECTED &&
            status != StoryCreation.CreationStatus.CONFIGURED) {
            throw new IllegalStateException(
                "Cannot select characters at current stage: " + status +
                ". Characters can only be selected after character analysis is complete and before story generation starts."
            );
        }

        // Validate story state
        if (storyCreation.getCharactersJson() == null || storyCreation.getCharactersJson().isBlank()) {
            throw new RuntimeException("No characters available for this story");
        }

        // Log if characters were already selected (overwriting)
        if (storyCreation.getSelectedCharactersForChatJson() != null &&
            !storyCreation.getSelectedCharactersForChatJson().isBlank()) {
            log.warn("Overwriting previously selected characters for story: {}", storyId);
        }

        try {
            // Parse existing characters
            List<CharacterDto> allCharacters = objectMapper.readValue(
                    storyCreation.getCharactersJson(),
                    new TypeReference<List<CharacterDto>>() {}
            );

            // Validate selected character names exist
            List<String> availableNames = allCharacters.stream()
                    .map(CharacterDto::getName)
                    .collect(Collectors.toList());

            for (String selectedName : request.getCharacterNames()) {
                if (!availableNames.contains(selectedName)) {
                    throw new IllegalArgumentException("Character not found: " + selectedName);
                }
            }

            // Save selected characters to StoryCreation
            storyCreation.setSelectedCharactersForChatJson(
                    objectMapper.writeValueAsString(request.getCharacterNames())
            );
            storyCreationRepository.save(storyCreation);

            // Index selected characters to NPC AI
            indexSelectedCharacters(storyCreation, allCharacters, request.getCharacterNames());

            log.info("Characters selected and indexed successfully");

        } catch (Exception e) {
            log.error("Failed to select and index characters", e);
            throw new RuntimeException("Failed to select characters: " + e.getMessage());
        }
    }

    private void indexSelectedCharacters(StoryCreation storyCreation,
                                        List<CharacterDto> allCharacters,
                                        List<String> selectedNames) {
        try {
            // Filter only selected characters
            List<CharacterDto> selectedCharacters = allCharacters.stream()
                    .filter(c -> selectedNames.contains(c.getName()))
                    .collect(Collectors.toList());

            // Build rich character description with all available information
            StringBuilder combinedDescription = new StringBuilder();

            combinedDescription.append("=== 소설 정보 ===").append(System.lineSeparator());
            combinedDescription.append("제목: ").append(storyCreation.getTitle()).append(System.lineSeparator());
            if (storyCreation.getGenre() != null) {
                combinedDescription.append("장르: ").append(storyCreation.getGenre()).append(System.lineSeparator());
            }
            combinedDescription.append(System.lineSeparator());

            // Add story summary
            if (storyCreation.getSummary() != null && !storyCreation.getSummary().isBlank()) {
                combinedDescription.append("=== 줄거리 요약 ===").append(System.lineSeparator());
                combinedDescription.append(storyCreation.getSummary()).append(System.lineSeparator());
                combinedDescription.append(System.lineSeparator());
            }

            // Add detailed character information
            combinedDescription.append("=== 등장인물 정보 ===").append(System.lineSeparator());
            for (CharacterDto character : selectedCharacters) {
                if (character.getName() != null) {
                    combinedDescription.append("■ ").append(character.getName()).append(System.lineSeparator());

                    // Aliases
                    if (character.getAliases() != null && !character.getAliases().isEmpty()) {
                        combinedDescription.append("  별칭: ").append(String.join(", ", character.getAliases()))
                                .append(System.lineSeparator());
                    }

                    // Description
                    if (character.getDescription() != null && !character.getDescription().isBlank()) {
                        combinedDescription.append("  설명: ").append(character.getDescription())
                                .append(System.lineSeparator());
                    }

                    // Relationships
                    if (character.getRelationships() != null && !character.getRelationships().isEmpty()) {
                        combinedDescription.append("  관계:").append(System.lineSeparator());
                        for (String relationship : character.getRelationships()) {
                            combinedDescription.append("    - ").append(relationship).append(System.lineSeparator());
                        }
                    }

                    combinedDescription.append(System.lineSeparator());
                }
            }

            // Create index request
            CharacterIndexRequestDto indexRequest = CharacterIndexRequestDto.builder()
                    .characterId(storyCreation.getId().toString())  // Use StoryCreation ID as session_id
                    .name(storyCreation.getTitle())
                    .description(combinedDescription.toString())
                    .personality(null)
                    .background(null)
                    .dialogueSamples(null)
                    .relationships(null)
                    .additionalInfo(java.util.Map.of(
                            "storyId", storyCreation.getId().toString(),
                            "genre", storyCreation.getGenre() != null ? storyCreation.getGenre() : "",
                            "selectedCharacters", String.join(", ", selectedNames)
                    ))
                    .build();

            // Call RagService to index - 실패해도 계속 진행
            Boolean result = ragService.indexCharacter(indexRequest);

            if (result) {
                log.info("Selected characters indexed successfully to NPC AI with StoryCreation ID: {}", storyCreation.getId());
            } else {
                log.warn("Character indexing to NPC AI failed for StoryCreation ID: {}. Chatbot may not work, but story creation can continue.", storyCreation.getId());
            }

        } catch (Exception e) {
            // 인덱싱 실패는 치명적이지 않으므로 경고만 로그
            log.warn("Failed to index selected characters (non-critical): {}. Story creation will continue.", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public SelectedCharactersResponseDto getSelectedCharacters(String storyId) {
        log.info("=== Get Selected Characters ===");
        log.info("StoryId: {}", storyId);

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        // Check if characters have been selected
        if (storyCreation.getSelectedCharactersForChatJson() == null ||
            storyCreation.getSelectedCharactersForChatJson().isBlank()) {
            return SelectedCharactersResponseDto.builder()
                    .hasSelection(false)
                    .selectedCharacterNames(List.of())
                    .selectedCharacters(List.of())
                    .build();
        }

        try {
            // Parse selected character names
            List<String> selectedNames = objectMapper.readValue(
                    storyCreation.getSelectedCharactersForChatJson(),
                    new TypeReference<List<String>>() {}
            );

            // Parse all characters
            List<CharacterDto> allCharacters = new ArrayList<>();
            if (storyCreation.getCharactersJson() != null && !storyCreation.getCharactersJson().isBlank()) {
                allCharacters = objectMapper.readValue(
                        storyCreation.getCharactersJson(),
                        new TypeReference<List<CharacterDto>>() {}
                );
            }

            // Filter selected characters
            List<CharacterDto> selectedCharacters = allCharacters.stream()
                    .filter(c -> selectedNames.contains(c.getName()))
                    .collect(Collectors.toList());

            return SelectedCharactersResponseDto.builder()
                    .hasSelection(true)
                    .selectedCharacterNames(selectedNames)
                    .selectedCharacters(selectedCharacters)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse selected characters", e);
            throw new RuntimeException("Failed to retrieve selected characters: " + e.getMessage());
        }
    }

    @Transactional
    public StoryConfigResponseDto configureStory(String storyId, StoryConfigRequestDto request) {
        log.info("=== Configure Story ===");
        log.info("StoryId: {}", storyId);

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        if (storyCreation.getStatus() != StoryCreation.CreationStatus.GAUGES_SELECTED) {
            throw new IllegalStateException("Cannot configure: current status is " + storyCreation.getStatus());
        }

        try {
            storyCreation.setDescription(request.getDescription());
            storyCreation.setNumEpisodes(request.getNumEpisodes());
            storyCreation.setMaxDepth(request.getMaxDepth());
            storyCreation.setNumEpisodeEndings(request.getNumEpisodeEndings());
            storyCreation.setStatus(StoryCreation.CreationStatus.CONFIGURED);
            storyCreation.setCurrentPhase("CONFIGURED");
            storyCreation.setProgressPercentage(50);
            storyCreation.setProgressMessage("Story configured");

            storyCreation.setCompletedEpisodes(0);
            storyCreation.setTotalEpisodesToGenerate(request.getNumEpisodes());

            storyCreation = storyCreationRepository.save(storyCreation);

            return StoryConfigResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .status(storyCreation.getStatus())
                    .config(StoryConfigResponseDto.ConfigData.builder()
                            .description(request.getDescription())
                            .numEpisodes(request.getNumEpisodes())
                            .maxDepth(request.getMaxDepth())
                            .numEpisodeEndings(request.getNumEpisodeEndings())
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Failed to configure story", e);
            throw new RuntimeException("Failed to configure story: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StoryProgressResponseDto getProgress(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        return StoryProgressResponseDto.builder()
                .storyId(storyCreation.getId())
                .status(storyCreation.getStatus())
                .progress(StoryProgressResponseDto.ProgressData.builder()
                        .currentPhase(storyCreation.getCurrentPhase())
                        .completedEpisodes(storyCreation.getCompletedEpisodes())
                        .totalEpisodes(storyCreation.getTotalEpisodesToGenerate())
                        .percentage(storyCreation.getProgressPercentage())
                        .message(storyCreation.getProgressMessage())
                        .error(storyCreation.getErrorMessage())
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public StoryResultResponseDto getResult(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        if (storyCreation.getStatus() != StoryCreation.CreationStatus.COMPLETED) {
            throw new IllegalStateException("Story generation is not completed yet");
        }

        StoryData storyData = storyDataRepository.findById(storyCreation.getStoryDataId())
                .orElseThrow(() -> new RuntimeException("Story data not found"));

        try {
            String storyJson = s3Service.downloadFileContent(storyData.getStoryFileKey());

            FullStoryDto fullStory = objectMapper.readValue(storyJson, FullStoryDto.class);
            EpisodeDto firstEpisode = fullStory.getEpisodes().stream()
                    .filter(ep -> ep.getOrder() == 1)
                    .findFirst()
                    .orElse(null);

            return StoryResultResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .status(storyCreation.getStatus())
                    .storyDataId(storyData.getId())
                    .metadata(StoryResultResponseDto.MetadataData.builder()
                            .title(storyData.getTitle())
                            .genre(storyData.getGenre())
                            .description(storyData.getDescription())
                            .totalEpisodes(storyData.getTotalEpisodes())
                            .totalNodes(storyData.getTotalNodes())
                            .totalGauges(2)
                            .createdAt(storyData.getCreatedAt())
                            .build())
                    .preview(StoryResultResponseDto.PreviewData.builder()
                            .firstEpisodeTitle(firstEpisode != null ? firstEpisode.getTitle() : null)
                            .firstEpisodeIntro(firstEpisode != null ? firstEpisode.getIntroText() : null)
                            .selectedGauges(null)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse story result", e);
            throw new RuntimeException("Failed to parse story result: " + e.getMessage());
        }
    }

    @Transactional
    public void updateStoryStatus(String storyId, StoryCreation.CreationStatus status, String errorMessage) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        storyCreation.setStatus(status);
        storyCreation.setErrorMessage(errorMessage);

        if (status == StoryCreation.CreationStatus.FAILED) {
            storyCreation.setCurrentPhase("FAILED");
            storyCreation.setProgressMessage("Failed: " + errorMessage);
        }

        storyCreationRepository.save(storyCreation);
    }

    @Transactional(readOnly = true)
    public FullStoryDto getFullStoryData(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        if (storyCreation.getStatus() != StoryCreation.CreationStatus.COMPLETED) {
            throw new IllegalStateException("Story generation is not completed yet");
        }

        StoryData storyData = storyDataRepository.findById(storyCreation.getStoryDataId())
                .orElseThrow(() -> new RuntimeException("Story data not found"));

        try {
            String storyJson = s3Service.downloadFileContent(storyData.getStoryFileKey());
            return objectMapper.readValue(storyJson, FullStoryDto.class);
        } catch (Exception e) {
            log.error("Failed to parse story JSON", e);
            throw new RuntimeException("Failed to parse story data: " + e.getMessage());
        }
    }

    @Transactional
    public StoryUploadResponseDto uploadNovelFromS3(S3UploadRequestDto request) {
        log.info("=== Upload Novel From S3 ===");
        log.info("Title: {}, FileKey: {}", request.getTitle(), request.getFileKey());

        String storyId = "story_" + UUID.randomUUID().toString().substring(0, 8);

        StoryCreation storyCreation = StoryCreation.builder()
                .id(storyId)
                .title(request.getTitle())
                .genre(request.getGenre())
                .novelText("")
                .s3FileKey(request.getFileKey())
                .status(StoryCreation.CreationStatus.ANALYZING)
                .currentPhase("ANALYZING")
                .progressPercentage(0)
                .progressMessage("Starting novel analysis from S3...")
                .build();

        storyCreation = storyCreationRepository.save(storyCreation);

        startAnalysisFromS3Async(storyId, request.getFileKey());

        return StoryUploadResponseDto.builder()
                .storyId(storyCreation.getId())
                .title(storyCreation.getTitle())
                .genre(storyCreation.getGenre())
                .status(storyCreation.getStatus())
                .createdAt(storyCreation.getCreatedAt())
                .build();
    }

    @Transactional
    public void startAnalysisFromS3Async(String storyId, String fileKey) {
        try {
            log.info("Starting AI analysis from S3 for story: {}, bucket: {}, fileKey: {}", storyId, bucketName, fileKey);

            // Generate pre-signed URL for AI server to upload analysis result
            String resultFileKey = "analysis/" + UUID.randomUUID().toString() + ".json";
            String s3UploadUrl = s3Service.generatePresignedUploadUrl(resultFileKey).getUrl();
            log.info("Generated Pre-signed URL for AI server to upload analysis result: {}", resultFileKey);

            // Generate pre-signed download URL for RAG server to access original novel
            String novelDownloadUrl = s3Service.generatePresignedDownloadUrl(fileKey);
            log.info("Generated Pre-signed download URL for RAG server to access original novel: {}", fileKey);

            // Index novel to RAG server (병렬 처리 - 실패해도 분석 계속 진행)
            StoryCreation storyCreationForRag = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story not found"));
            NovelIndexRequestDto ragRequest = NovelIndexRequestDto.builder()
                    .storyId(storyId)
                    .title(storyCreationForRag.getTitle())
                    .fileKey(fileKey)
                    .bucket(bucketName)
                    .build();
            ragService.indexNovel(ragRequest);

            NovelAnalysisRequestDto aiRequest = NovelAnalysisRequestDto.builder()
                    .fileKey(fileKey)
                    .bucket(bucketName)
                    .s3UploadUrl(s3UploadUrl)
                    .resultFileKey(resultFileKey)
                    .novelDownloadUrl(novelDownloadUrl)  // RAG가 원본 소설을 다운로드할 URL
                    .build();

            log.info("Calling relay-server /ai/analyze-from-s3 endpoint for S3 mode");

            NovelAnalysisResponseDto response = relayServerWebClient.post()
                    .uri("/ai/analyze-from-s3")  // S3 전용 엔드포인트 사용
                    .bodyValue(aiRequest)
                    .retrieve()
                    .bodyToMono(NovelAnalysisResponseDto.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("No response from AI server");
            }

            StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story not found"));

            // [수정] S3 모드일 때도 데이터를 다운로드하여 DB에 저장 (누락 방지)
            if (response.isS3Mode()) {
                log.info("AI server uploaded analysis result to S3 directly: {}", response.getFileKey());
                storyCreation.setAnalysisResultFileKey(response.getFileKey());

                // --- 필수 데이터 DB 저장 ---
                try {
                    String analysisJson = s3Service.downloadFileContent(response.getFileKey());
                    NovelAnalysisResponseDto analysisData = objectMapper.readValue(analysisJson, NovelAnalysisResponseDto.class);

                    storyCreation.setSummary(analysisData.getSummary());
                    storyCreation.setCharactersJson(objectMapper.writeValueAsString(analysisData.getCharacters()));
                    storyCreation.setGaugesJson(objectMapper.writeValueAsString(analysisData.getGauges()));
                    // Note: finalEndings will be generated after user selects gauges (in selectGauges method)

                    log.info("Synced analysis data from S3 to DB for story: {}", storyId);
                } catch (Exception e) {
                    log.error("Failed to sync analysis data from S3 to DB", e);
                    throw new RuntimeException("Failed to sync analysis data: " + e.getMessage());
                }
            }
            else {
                storyCreation.setSummary(response.getSummary());
                storyCreation.setCharactersJson(objectMapper.writeValueAsString(response.getCharacters()));
                storyCreation.setGaugesJson(objectMapper.writeValueAsString(response.getGauges()));
                // Note: finalEndings will be generated after user selects gauges (in selectGauges method)
            }

            storyCreation.setStatus(StoryCreation.CreationStatus.GAUGES_READY);
            storyCreation.setCurrentPhase("GAUGES_READY");
            storyCreation.setProgressPercentage(30);
            storyCreation.setProgressMessage("Analysis completed. Ready for gauge selection.");

            storyCreationRepository.save(storyCreation);

            log.info("AI analysis from S3 completed for story: {}", storyId);

        } catch (Exception e) {
            log.error("Failed to analyze novel from S3", e);

            StoryCreation storyCreation = storyCreationRepository.findById(storyId).orElse(null);
            if (storyCreation != null) {
                storyCreation.setStatus(StoryCreation.CreationStatus.FAILED);
                storyCreation.setCurrentPhase("FAILED");
                storyCreation.setProgressMessage("Analysis failed: " + e.getMessage());
                storyCreationRepository.save(storyCreation);
            }
        }
    }
}