package com.story.game.creation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.ai.dto.NovelStyleLearnRequestDto;
import com.story.game.ai.service.RelayServerClient;
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
import jakarta.persistence.EntityNotFoundException;
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
    private final RelayServerClient relayServerClient;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final com.story.game.story.repository.EpisodeRepository episodeRepository;

    @org.springframework.beans.factory.annotation.Value("${aws.s3.bucket}")
    private String bucketName;

    @Transactional
    public StoryUploadResponseDto uploadNovel(StoryUploadRequestDto request, com.story.game.auth.entity.User user) {
        log.info("=== Upload Novel ===");
        log.info("Title: {}, User: {}", request.getTitle(), user != null ? user.getUsername() : "null");

        String storyId = "story_" + UUID.randomUUID().toString().substring(0, 8);

        StoryCreation storyCreation = StoryCreation.builder()
                .id(storyId)
                .user(user)
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
                throw new com.story.game.common.exception.ExternalServiceException("No response from AI server");
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

            // Learn novel style for image generation (non-blocking, failure is non-critical)
            try {
                NovelStyleLearnRequestDto styleRequest = NovelStyleLearnRequestDto.builder()
                        .story_id(storyId)
                        .novel_text(novelText)
                        .title(storyCreation.getTitle())
                        .build();

                Boolean styleResult = relayServerClient.learnNovelStyle(styleRequest);
                if (styleResult) {
                    log.info("Novel style learned successfully for story: {}", storyId);
                } else {
                    log.warn("Novel style learning failed for story: {} (non-critical)", storyId);
                }
            } catch (Exception e) {
                log.warn("Failed to learn novel style (non-critical): {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to analyze novel for story: {}", storyId, e);
            updateStoryStatus(storyId, StoryCreation.CreationStatus.FAILED, "Analysis failed: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StorySummaryResponseDto getSummary(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
                throw new com.story.game.common.exception.InvalidStateException("No gauges found");
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
            throw new com.story.game.common.exception.InvalidStateException("Failed to select gauges: " + e.getMessage());
        }
    }

    @Transactional
    public void selectAndIndexCharacters(String storyId, SelectCharactersRequestDto request) {
        log.info("=== Select and Index Characters ===");
        log.info("StoryId: {}, Selected characters: {}", storyId, request.getCharacterNames());

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
            throw new com.story.game.common.exception.InvalidStateException("No characters available for this story");
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
            throw new com.story.game.common.exception.ExternalServiceException("Failed to select characters: " + e.getMessage());
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

            // ========== STEP 1: 소설 학습 (RAG 시스템에 소설 원본 인덱싱) ==========
            if (storyCreation.getS3FileKey() != null && !storyCreation.getS3FileKey().isBlank()) {
                log.info("=== Indexing Novel to RAG System ===");
                log.info("Story: {} ({})", storyCreation.getTitle(), storyCreation.getId());
                log.info("S3 File Key: {}", storyCreation.getS3FileKey());

                try {
                    NovelIndexRequestDto novelIndexRequest = NovelIndexRequestDto.builder()
                            .storyId(storyCreation.getId())
                            .title(storyCreation.getTitle())
                            .fileKey(storyCreation.getS3FileKey())
                            .bucket(bucketName)
                            .build();

                    Boolean novelIndexResult = ragService.indexNovel(novelIndexRequest);

                    if (novelIndexResult) {
                        log.info("✅ Novel indexed successfully for story: {}", storyCreation.getId());
                    } else {
                        log.warn("⚠️ Novel indexing failed for story: {}", storyCreation.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to index novel (non-critical): {}", e.getMessage(), e);
                }
            } else {
                log.warn("⚠️ No S3 file key found for story: {}. Skipping novel indexing.", storyCreation.getId());
            }

            // ========== STEP 2: 캐릭터 정보 설정 ==========
            // Build story context (공통 정보)
            StringBuilder storyContext = new StringBuilder();
            storyContext.append("=== 소설 정보 ===").append(System.lineSeparator());
            storyContext.append("제목: ").append(storyCreation.getTitle()).append(System.lineSeparator());
            if (storyCreation.getGenre() != null) {
                storyContext.append("장르: ").append(storyCreation.getGenre()).append(System.lineSeparator());
            }
            storyContext.append(System.lineSeparator());

            // Add story summary
            if (storyCreation.getSummary() != null && !storyCreation.getSummary().isBlank()) {
                storyContext.append("=== 줄거리 요약 ===").append(System.lineSeparator());
                storyContext.append(storyCreation.getSummary()).append(System.lineSeparator());
                storyContext.append(System.lineSeparator());
            }

            // 다른 캐릭터 정보 추가 (관계 파악용)
            storyContext.append("=== 주요 등장인물 ===").append(System.lineSeparator());
            for (CharacterDto character : selectedCharacters) {
                if (character.getName() != null) {
                    storyContext.append("- ").append(character.getName());
                    if (character.getDescription() != null && !character.getDescription().isBlank()) {
                        storyContext.append(": ").append(character.getDescription());
                    }
                    storyContext.append(System.lineSeparator());
                }
            }
            storyContext.append(System.lineSeparator());

            // 각 캐릭터마다 개별적으로 설정
            int successCount = 0;
            int failCount = 0;

            for (CharacterDto character : selectedCharacters) {
                if (character.getName() == null || character.getName().isBlank()) {
                    continue;
                }

                try {
                    // Generate unique character ID: {storyId}_{characterName}
                    String characterId = storyCreation.getId() + "_" + character.getName();

                    // Build character-specific description
                    StringBuilder characterDescription = new StringBuilder();

                    // Add story context
                    characterDescription.append(storyContext);

                    // Add specific character information
                    characterDescription.append("=== 이 캐릭터의 상세 정보 ===").append(System.lineSeparator());
                    characterDescription.append("이름: ").append(character.getName()).append(System.lineSeparator());

                    // Aliases
                    if (character.getAliases() != null && !character.getAliases().isEmpty()) {
                        characterDescription.append("별칭: ").append(String.join(", ", character.getAliases()))
                                .append(System.lineSeparator());
                    }

                    // Description
                    if (character.getDescription() != null && !character.getDescription().isBlank()) {
                        characterDescription.append("성격 및 특징: ").append(character.getDescription())
                                .append(System.lineSeparator());
                    }

                    // Relationships
                    if (character.getRelationships() != null && !character.getRelationships().isEmpty()) {
                        characterDescription.append("관계:").append(System.lineSeparator());
                        for (String relationship : character.getRelationships()) {
                            characterDescription.append("  - ").append(relationship).append(System.lineSeparator());
                        }
                    }

                    // Call RagService to set character information (not indexing)
                    com.story.game.rag.dto.CharacterSetRequestDto setRequest =
                        com.story.game.rag.dto.CharacterSetRequestDto.builder()
                            .characterId(characterId)
                            .characterName(character.getName())
                            .characterDescription(characterDescription.toString())
                            .build();

                    Boolean result = ragService.setCharacter(setRequest);

                    if (result) {
                        successCount++;
                        log.info("✅ Character '{}' set successfully with ID: {}", character.getName(), characterId);
                    } else {
                        failCount++;
                        log.warn("⚠️ Character '{}' setting failed with ID: {}", character.getName(), characterId);
                    }

                } catch (Exception e) {
                    failCount++;
                    log.warn("Failed to set character '{}': {}", character.getName(), e.getMessage());
                }
            }

            log.info("Character setting completed - Success: {}, Failed: {}", successCount, failCount);

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
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

        // Check if characters have been selected
        if (storyCreation.getSelectedCharactersForChatJson() == null ||
            storyCreation.getSelectedCharactersForChatJson().isBlank()) {
            return SelectedCharactersResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .storyDataId(storyCreation.getStoryDataId())
                    .chatCharacterId(storyCreation.getId())  // NPC 대화용 - storyId와 동일
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

            // Filter selected characters and assign chatCharacterId to each
            List<CharacterDto> selectedCharacters = allCharacters.stream()
                    .filter(c -> selectedNames.contains(c.getName()))
                    .map(c -> {
                        // Generate unique chatCharacterId for each character
                        String chatCharId = storyCreation.getId() + "_" + c.getName();
                        return CharacterDto.builder()
                                .name(c.getName())
                                .aliases(c.getAliases())
                                .description(c.getDescription())
                                .relationships(c.getRelationships())
                                .chatCharacterId(chatCharId)  // Assign unique ID
                                .build();
                    })
                    .collect(Collectors.toList());

            return SelectedCharactersResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .storyDataId(storyCreation.getStoryDataId())
                    .chatCharacterId(null)  // Deprecated - 이제 각 캐릭터가 고유 ID를 가짐
                    .hasSelection(true)
                    .selectedCharacterNames(selectedNames)
                    .selectedCharacters(selectedCharacters)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse selected characters", e);
            throw new com.story.game.common.exception.InvalidStateException("Failed to retrieve selected characters: " + e.getMessage());
        }
    }

    @Transactional
    public StoryConfigResponseDto configureStory(String storyId, StoryConfigRequestDto request) {
        log.info("=== Configure Story ===");
        log.info("StoryId: {}", storyId);

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
            throw new com.story.game.common.exception.InvalidStateException("Failed to configure story: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public StoryProgressResponseDto getProgress(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
            throw new com.story.game.common.exception.InvalidStateException("Failed to parse story result: " + e.getMessage());
        }
    }

    @Transactional
    public void updateStoryStatus(String storyId, StoryCreation.CreationStatus status, String errorMessage) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found"));

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
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

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
            throw new com.story.game.common.exception.InvalidStateException("Failed to parse story data: " + e.getMessage());
        }
    }

    @Transactional
    public StoryUploadResponseDto uploadNovelFromS3(S3UploadRequestDto request, com.story.game.auth.entity.User user) {
        log.info("=== Upload Novel From S3 ===");
        log.info("Title: {}, FileKey: {}, User: {}", request.getTitle(), request.getFileKey(), user != null ? user.getUsername() : "null");

        String storyId = "story_" + UUID.randomUUID().toString().substring(0, 8);

        StoryCreation storyCreation = StoryCreation.builder()
                .id(storyId)
                .user(user)
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
                throw new com.story.game.common.exception.ExternalServiceException("No response from AI server");
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
                    throw new com.story.game.common.exception.InvalidStateException("Failed to sync analysis data: " + e.getMessage());
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

            // Learn novel style for image generation (non-blocking, failure is non-critical)
            try {
                String novelTextForStyle = s3Service.downloadFileContent(fileKey);
                NovelStyleLearnRequestDto styleRequest = NovelStyleLearnRequestDto.builder()
                        .story_id(storyId)
                        .novel_text(novelTextForStyle)
                        .title(storyCreation.getTitle())
                        .build();

                Boolean styleResult = relayServerClient.learnNovelStyle(styleRequest);
                if (styleResult) {
                    log.info("Novel style learned successfully for story: {}", storyId);
                } else {
                    log.warn("Novel style learning failed for story: {} (non-critical)", storyId);
                }
            } catch (Exception e) {
                log.warn("Failed to learn novel style (non-critical): {}", e.getMessage());
            }

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

    /**
     * 스토리 삭제 (생성 중이거나 완료된 스토리)
     * - StoryCreation, StoryData, Episodes, Nodes, Choices 모두 삭제
     * - S3에 저장된 파일들도 삭제 (소설 원본, 분석 결과, 스토리 JSON, 이미지 등)
     */
    @Transactional
    public void deleteStory(String storyId, com.story.game.auth.entity.User user) {
        log.info("=== Delete Story ===");
        log.info("StoryId: {}, User: {}", storyId, user.getUsername());

        // 1. Find story creation
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new com.story.game.common.exception.ResourceNotFoundException("Story not found: " + storyId));

        // 2. Authorization check - 본인이 생성한 스토리만 삭제 가능
        if (storyCreation.getUser() == null) {
            throw new com.story.game.common.exception.UnauthorizedException("Unauthorized: Story has no owner");
        }

        if (!storyCreation.getUser().getId().equals(user.getId())) {
            throw new com.story.game.common.exception.UnauthorizedException("Unauthorized: You can only delete your own stories");
        }

        // 3. S3 파일 삭제 (실패해도 계속 진행)
        deleteS3FilesForStory(storyCreation);

        // 3-1. RAG 서버 데이터 삭제 (실패해도 계속 진행)
        try {
            ragService.deleteStoryFromRag(storyId);
            log.info("RAG data deletion requested for story: {}", storyId);
        } catch (Exception e) {
            log.warn("Failed to delete RAG data (non-critical), continuing with deletion: {}", storyId, e);
        }

        // 3-2. Chat Conversations 삭제 (DB에 저장된 대화 내역)
        try {
            ragService.deleteConversationsByStoryId(user.getUsername(), storyId);
            log.info("Chat conversations deleted for story: {}", storyId);
        } catch (Exception e) {
            log.warn("Failed to delete chat conversations (non-critical): {}", storyId, e);
        }

        // 4. StoryData 삭제 (완료된 스토리의 경우)
        if (storyCreation.getStoryDataId() != null) {
            storyDataRepository.findById(storyCreation.getStoryDataId()).ifPresent(storyData -> {
                log.info("Deleting StoryData: {}", storyData.getId());

                // StoryData의 S3 파일도 삭제
                if (storyData.getStoryFileKey() != null) {
                    deleteS3File(storyData.getStoryFileKey(), "Story JSON");
                }
                if (storyData.getThumbnailFileKey() != null) {
                    deleteS3File(storyData.getThumbnailFileKey(), "Thumbnail");
                }

                storyDataRepository.delete(storyData);
            });
        }

        // 5. StoryCreation 삭제 (연관된 Episodes, Nodes, Choices는 cascade로 자동 삭제됨)
        log.info("Deleting StoryCreation: {}", storyId);
        storyCreationRepository.delete(storyCreation);

        log.info("Story deleted successfully: {}", storyId);
    }

    /**
     * 스토리와 연관된 S3 파일들 삭제
     */
    private void deleteS3FilesForStory(StoryCreation storyCreation) {
        log.info("Deleting S3 files for story: {}", storyCreation.getId());

        // 소설 원본 파일
        if (storyCreation.getS3FileKey() != null) {
            deleteS3File(storyCreation.getS3FileKey(), "Novel original");
        }

        // 분석 결과 파일
        if (storyCreation.getAnalysisResultFileKey() != null) {
            deleteS3File(storyCreation.getAnalysisResultFileKey(), "Analysis result");
        }

        // 노드 이미지 파일들 삭제
        try {
            List<com.story.game.story.entity.Episode> episodes = episodeRepository.findAllByStory(storyCreation);
            int imageCount = 0;

            for (com.story.game.story.entity.Episode episode : episodes) {
                for (com.story.game.story.entity.StoryNode node : episode.getNodes()) {
                    if (node.getImageFileKey() != null && !node.getImageFileKey().isBlank()) {
                        deleteS3File(node.getImageFileKey(), "Node image");
                        imageCount++;
                    }
                }
            }

            log.info("Deleted {} node images for story: {}", imageCount, storyCreation.getId());
        } catch (Exception e) {
            log.warn("Failed to delete some node images (non-critical): {}", storyCreation.getId(), e);
        }
    }

    /**
     * S3 파일 삭제 (실패해도 로그만 남기고 계속 진행)
     */
    private void deleteS3File(String fileKey, String fileType) {
        try {
            s3Service.deleteFile(fileKey);
            log.info("Deleted {} from S3: {}", fileType, fileKey);
        } catch (Exception e) {
            log.warn("Failed to delete {} from S3: {} (continuing anyway)", fileType, fileKey, e);
        }
    }
}