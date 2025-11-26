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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryManagementService {

    private final StoryCreationRepository storyCreationRepository;
    private final StoryDataRepository storyDataRepository;
    private final WebClient aiServerWebClient;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

    /**
     * 1. 소설 업로드 및 분석 시작
     */
    @Transactional
    public StoryUploadResponseDto uploadNovel(StoryUploadRequestDto request) {
        log.info("=== Upload Novel ===");
        log.info("Title: {}", request.getTitle());

        // 고유 ID 생성
        String storyId = "story_" + UUID.randomUUID().toString().substring(0, 8);

        // StoryCreation 엔티티 생성
        StoryCreation storyCreation = StoryCreation.builder()
                .id(storyId)
                .title(request.getTitle())
                .novelText(request.getNovelText())
                .status(StoryCreation.CreationStatus.ANALYZING)
                .currentPhase("ANALYZING")
                .progressPercentage(0)
                .progressMessage("Starting novel analysis...")
                .build();

        storyCreation = storyCreationRepository.save(storyCreation);

        // 비동기로 AI 분석 시작 (별도 메서드에서 처리)
        startAnalysisAsync(storyId, request.getNovelText());

        return StoryUploadResponseDto.builder()
                .storyId(storyCreation.getId())
                .title(storyCreation.getTitle())
                .status(storyCreation.getStatus())
                .createdAt(storyCreation.getCreatedAt())
                .build();
    }

    /**
     * 비동기 분석 시작 (실제로는 별도 스레드나 메시지 큐로 처리해야 함)
     * 현재는 동기 처리로 구현
     */
    @Transactional
    public void startAnalysisAsync(String storyId, String novelText) {
        try {
            log.info("Starting AI analysis for story: {}", storyId);

            // AI 서버에 분석 요청
            NovelAnalysisRequestDto request = NovelAnalysisRequestDto.builder()
                    .novelText(novelText)
                    .build();

            NovelAnalysisResponseDto response = aiServerWebClient.post()
                    .uri("/analyze")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(NovelAnalysisResponseDto.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("No response from AI server");
            }

            // 결과 저장
            StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story not found"));

            storyCreation.setSummary(response.getSummary());
            storyCreation.setCharactersJson(objectMapper.writeValueAsString(response.getCharacters()));
            storyCreation.setGaugesJson(objectMapper.writeValueAsString(response.getGauges()));
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

    /**
     * 2. 요약 조회
     */
    @Transactional(readOnly = true)
    public StorySummaryResponseDto getSummary(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        return StorySummaryResponseDto.builder()
                .storyId(storyCreation.getId())
                .status(storyCreation.getStatus())
                .summary(storyCreation.getSummary())
                .build();
    }

    /**
     * 3. 캐릭터 조회
     */
    @Transactional(readOnly = true)
    public StoryCharactersResponseDto getCharacters(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        List<CharacterDto> characters = null;
        if (storyCreation.getCharactersJson() != null) {
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

    /**
     * 4. 게이지 제안 조회
     */
    @Transactional(readOnly = true)
    public StoryGaugesResponseDto getGauges(String storyId) {
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        List<GaugeDto> gauges = null;
        if (storyCreation.getGaugesJson() != null) {
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

    /**
     * 5. 게이지 선택
     */
    @Transactional
    public GaugeSelectionResponseDto selectGauges(String storyId, GaugeSelectionRequestDto request) {
        log.info("=== Select Gauges ===");
        log.info("StoryId: {}, Selected: {}", storyId, request.getSelectedGaugeIds());

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        // 상태 검증
        if (storyCreation.getStatus() != StoryCreation.CreationStatus.GAUGES_READY) {
            throw new IllegalStateException("Cannot select gauges: current status is " + storyCreation.getStatus());
        }

        try {
            // 선택된 게이지 저장
            storyCreation.setSelectedGaugeIdsJson(
                    objectMapper.writeValueAsString(request.getSelectedGaugeIds())
            );
            storyCreation.setStatus(StoryCreation.CreationStatus.GAUGES_SELECTED);
            storyCreation.setCurrentPhase("GAUGES_SELECTED");
            storyCreation.setProgressPercentage(40);
            storyCreation.setProgressMessage("Gauges selected");

            storyCreation = storyCreationRepository.save(storyCreation);

            // 선택된 게이지 정보 반환
            List<GaugeDto> allGauges = objectMapper.readValue(
                    storyCreation.getGaugesJson(),
                    new TypeReference<List<GaugeDto>>() {}
            );

            List<GaugeDto> selectedGauges = allGauges.stream()
                    .filter(g -> request.getSelectedGaugeIds().contains(g.getId()))
                    .toList();

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

    /**
     * 6. 생성 설정
     */
    @Transactional
    public StoryConfigResponseDto configureStory(String storyId, StoryConfigRequestDto request) {
        log.info("=== Configure Story ===");
        log.info("StoryId: {}", storyId);

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        // 상태 검증
        if (storyCreation.getStatus() != StoryCreation.CreationStatus.GAUGES_SELECTED) {
            throw new IllegalStateException("Cannot configure: current status is " + storyCreation.getStatus());
        }

        try {
            // 설정 저장
            storyCreation.setDescription(request.getDescription());
            storyCreation.setNumEpisodes(request.getNumEpisodes());
            storyCreation.setMaxDepth(request.getMaxDepth());
            storyCreation.setEndingConfigJson(
                    objectMapper.writeValueAsString(request.getEndingConfig())
            );
            storyCreation.setNumEpisodeEndings(request.getNumEpisodeEndings());
            storyCreation.setStatus(StoryCreation.CreationStatus.CONFIGURED);
            storyCreation.setCurrentPhase("CONFIGURED");
            storyCreation.setProgressPercentage(50);
            storyCreation.setProgressMessage("Story configured");

            storyCreation = storyCreationRepository.save(storyCreation);

            return StoryConfigResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .status(storyCreation.getStatus())
                    .config(StoryConfigResponseDto.ConfigData.builder()
                            .description(request.getDescription())
                            .numEpisodes(request.getNumEpisodes())
                            .maxDepth(request.getMaxDepth())
                            .endingConfig(request.getEndingConfig())
                            .numEpisodeEndings(request.getNumEpisodeEndings())
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Failed to configure story", e);
            throw new RuntimeException("Failed to configure story: " + e.getMessage());
        }
    }

    /**
     * 7. 스토리 생성 시작
     */
    @Transactional
    public StoryGenerationStartResponseDto startGeneration(String storyId) {
        log.info("=== Start Story Generation ===");
        log.info("StoryId: {}", storyId);

        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyId));

        // 상태 검증
        if (storyCreation.getStatus() != StoryCreation.CreationStatus.CONFIGURED) {
            throw new IllegalStateException("Cannot start generation: current status is " + storyCreation.getStatus());
        }

        // 상태 업데이트
        storyCreation.setStatus(StoryCreation.CreationStatus.GENERATING);
        storyCreation.setCurrentPhase("GENERATING");
        storyCreation.setProgressPercentage(50);
        storyCreation.setProgressMessage("Starting story generation...");
        storyCreation.setTotalEpisodesToGenerate(storyCreation.getNumEpisodes());
        storyCreation.setCompletedEpisodes(0);

        storyCreationRepository.save(storyCreation);

        // 비동기로 생성 시작
        startGenerationAsync(storyId);

        return StoryGenerationStartResponseDto.builder()
                .storyId(storyId)
                .status(StoryCreation.CreationStatus.GENERATING)
                .message("Story generation started")
                .estimatedTime("5-10 minutes")
                .build();
    }

    /**
     * 비동기 생성 시작
     */
    @Transactional
    public void startGenerationAsync(String storyId) {
        try {
            StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story not found"));

            // AI 서버에 생성 요청
            List<String> selectedGaugeIds = objectMapper.readValue(
                    storyCreation.getSelectedGaugeIdsJson(),
                    new TypeReference<List<String>>() {}
            );

            Map<String, Integer> endingConfig = objectMapper.readValue(
                    storyCreation.getEndingConfigJson(),
                    new TypeReference<Map<String, Integer>>() {}
            );

            // S3에 결과를 업로드할 Pre-signed URL 생성
            String resultFileKey = "stories/" + UUID.randomUUID().toString() + ".json";
            String s3UploadUrl = s3Service.generatePresignedUploadUrl(resultFileKey).getUrl();
            log.info("Generated Pre-signed URL for AI server to upload result: {}", resultFileKey);

            // S3 파일인 경우와 일반 텍스트인 경우 구분
            StoryGenerationRequestDto.StoryGenerationRequestDtoBuilder requestBuilder = StoryGenerationRequestDto.builder()
                    .selectedGaugeIds(selectedGaugeIds)
                    .numEpisodes(storyCreation.getNumEpisodes())
                    .maxDepth(storyCreation.getMaxDepth())
                    .endingConfig(endingConfig)
                    .numEpisodeEndings(storyCreation.getNumEpisodeEndings())
                    .s3UploadUrl(s3UploadUrl)
                    .s3FileKey(resultFileKey);

            String aiEndpoint;
            if (storyCreation.getS3FileKey() != null && !storyCreation.getS3FileKey().isEmpty()) {
                // S3 파일 사용
                requestBuilder.fileKey(storyCreation.getS3FileKey())
                             .bucket("story-game-bucket");
                aiEndpoint = "/generate-from-s3";
                log.info("Calling AI server for story generation from S3: {}", storyCreation.getS3FileKey());
            } else {
                // 일반 텍스트 사용
                requestBuilder.novelText(storyCreation.getNovelText());
                aiEndpoint = "/generate";
                log.info("Calling AI server for story generation with direct text");
            }

            StoryGenerationRequestDto request = requestBuilder.build();

            StoryGenerationResponseDto response = aiServerWebClient.post()
                    .uri(aiEndpoint)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(StoryGenerationResponseDto.class)
                    .block();

            if (response == null || !"success".equals(response.getStatus())) {
                throw new RuntimeException("Story generation failed");
            }

            String finalFileKey;
            Integer totalEpisodes;
            Integer totalNodes;

            // 새로운 방식: AI 서버가 S3에 직접 업로드 (권장)
            if (response.getFileKey() != null && response.getMetadata() != null) {
                log.info("AI server uploaded result to S3 directly: {}", response.getFileKey());
                finalFileKey = response.getFileKey();
                totalEpisodes = response.getMetadata().getTotalEpisodes();
                totalNodes = response.getMetadata().getTotalNodes();
            }
            // 레거시 방식: AI 서버가 전체 JSON 반환 (하위 호환성)
            else if (response.getData() != null) {
                log.warn("AI server returned full JSON (legacy mode). Consider upgrading to direct S3 upload.");
                String storyJson = objectMapper.writeValueAsString(response.getData());

                // 백엔드가 S3에 업로드
                finalFileKey = "stories/" + UUID.randomUUID().toString() + ".json";
                s3Service.uploadFile(finalFileKey, storyJson);

                totalEpisodes = response.getData().getMetadata().getTotalEpisodes();
                totalNodes = response.getData().getMetadata().getTotalNodes();
            }
            else {
                throw new RuntimeException("Invalid response from AI server: no data or fileKey provided");
            }

            StoryData storyData = StoryData.builder()
                    .title(storyCreation.getTitle())
                    .description(storyCreation.getDescription())
                    .storyFileKey(finalFileKey)
                    .totalEpisodes(totalEpisodes)
                    .totalNodes(totalNodes)
                    .build();

            storyData = storyDataRepository.save(storyData);

            // StoryCreation 업데이트
            storyCreation.setStoryDataId(storyData.getId());
            storyCreation.setStatus(StoryCreation.CreationStatus.COMPLETED);
            storyCreation.setCurrentPhase("COMPLETED");
            storyCreation.setProgressPercentage(100);
            storyCreation.setProgressMessage("Story generation completed");
            storyCreation.setCompletedAt(LocalDateTime.now());

            storyCreationRepository.save(storyCreation);

            log.info("Story generation completed: storyDataId={}", storyData.getId());

        } catch (Exception e) {
            log.error("Failed to generate story", e);
            updateStoryStatus(storyId, StoryCreation.CreationStatus.FAILED, "Generation failed: " + e.getMessage());
        }
    }

    /**
     * 8. 생성 진행률 조회
     */
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

    /**
     * 9. 생성 완료 결과 조회
     */
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
            // S3에서 스토리 JSON 다운로드
            String storyJson = s3Service.downloadFileContent(storyData.getStoryFileKey());

            // 첫 에피소드 정보 추출
            FullStoryDto fullStory = objectMapper.readValue(storyJson, FullStoryDto.class);
            EpisodeDto firstEpisode = fullStory.getEpisodes().stream()
                    .filter(ep -> ep.getOrder() == 1)
                    .findFirst()
                    .orElse(null);

            List<GaugeDto> selectedGauges = objectMapper.readValue(
                    storyCreation.getSelectedGaugeIdsJson(),
                    new TypeReference<List<String>>() {}
            ).stream()
                    .map(id -> fullStory.getContext().getSelectedGauges().stream()
                            .filter(g -> g.getId().equals(id))
                            .findFirst()
                            .orElse(null))
                    .toList();

            return StoryResultResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .status(storyCreation.getStatus())
                    .storyDataId(storyData.getId())
                    .metadata(StoryResultResponseDto.MetadataData.builder()
                            .title(storyData.getTitle())
                            .description(storyData.getDescription())
                            .totalEpisodes(storyData.getTotalEpisodes())
                            .totalNodes(storyData.getTotalNodes())
                            .totalGauges(2)
                            .createdAt(storyData.getCreatedAt())
                            .build())
                    .preview(StoryResultResponseDto.PreviewData.builder()
                            .firstEpisodeTitle(firstEpisode != null ? firstEpisode.getTitle() : null)
                            .firstEpisodeIntro(firstEpisode != null ? firstEpisode.getIntroText() : null)
                            .selectedGauges(selectedGauges)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse story result", e);
            throw new RuntimeException("Failed to parse story result: " + e.getMessage());
        }
    }

    /**
     * 상태 업데이트 헬퍼 메서드
     */
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

    /**
     * 10. 전체 스토리 데이터 조회 (게임 구성용)
     */
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
            // S3에서 스토리 JSON 다운로드
            String storyJson = s3Service.downloadFileContent(storyData.getStoryFileKey());
            return objectMapper.readValue(storyJson, FullStoryDto.class);
        } catch (Exception e) {
            log.error("Failed to parse story JSON", e);
            throw new RuntimeException("Failed to parse story data: " + e.getMessage());
        }
    }

    /**
     * 11. S3에서 소설 읽어서 업로드 (S3 Pre-signed URL 방식)
     * AI 서버가 S3에서 직접 다운로드하도록 fileKey만 전달
     */
    @Transactional
    public StoryUploadResponseDto uploadNovelFromS3(S3UploadRequestDto request) {
        log.info("=== Upload Novel From S3 ===");
        log.info("Title: {}, FileKey: {}", request.getTitle(), request.getFileKey());

        // 고유 ID 생성
        String storyId = "story_" + UUID.randomUUID().toString().substring(0, 8);

        // StoryCreation 엔티티 생성
        StoryCreation storyCreation = StoryCreation.builder()
                .id(storyId)
                .title(request.getTitle())
                .novelText("") // S3 fileKey로 대체, 텍스트는 저장하지 않음
                .s3FileKey(request.getFileKey()) // fileKey 저장
                .status(StoryCreation.CreationStatus.ANALYZING)
                .currentPhase("ANALYZING")
                .progressPercentage(0)
                .progressMessage("Starting novel analysis from S3...")
                .build();

        storyCreation = storyCreationRepository.save(storyCreation);

        // AI 서버에 fileKey만 전달하여 비동기 분석 시작
        startAnalysisFromS3Async(storyId, request.getFileKey());

        return StoryUploadResponseDto.builder()
                .storyId(storyCreation.getId())
                .title(storyCreation.getTitle())
                .status(storyCreation.getStatus())
                .createdAt(storyCreation.getCreatedAt())
                .build();
    }

    /**
     * S3 파일로 AI 분석 시작 (fileKey만 전달)
     */
    @Transactional
    public void startAnalysisFromS3Async(String storyId, String fileKey) {
        try {
            log.info("Starting AI analysis from S3 for story: {}, fileKey: {}", storyId, fileKey);

            // AI 서버에 fileKey만 전달 (AI 서버가 S3에서 직접 다운로드)
            NovelAnalysisRequestDto aiRequest = NovelAnalysisRequestDto.builder()
                    .fileKey(fileKey)
                    .bucket("story-game-bucket")
                    .build();

            NovelAnalysisResponseDto response = aiServerWebClient.post()
                    .uri("/analyze-from-s3")  // S3 전용 엔드포인트
                    .bodyValue(aiRequest)
                    .retrieve()
                    .bodyToMono(NovelAnalysisResponseDto.class)
                    .block();

            if (response == null) {
                throw new RuntimeException("No response from AI server");
            }

            // 결과 저장
            StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new RuntimeException("Story not found"));

            storyCreation.setSummary(response.getSummary());
            storyCreation.setCharactersJson(objectMapper.writeValueAsString(response.getCharacters()));
            storyCreation.setGaugesJson(objectMapper.writeValueAsString(response.getGauges()));
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
