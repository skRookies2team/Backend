package com.story.game.creation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.CharacterDto;
import com.story.game.common.dto.EpisodeDto;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.StoryNodeDto;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.creation.dto.StoryProgressResponseDto;
import com.story.game.creation.dto.TaskStartResponseDto;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.infrastructure.s3.S3Service;
import com.story.game.story.entity.Episode;
import com.story.game.story.mapper.StoryMapper;
import com.story.game.story.repository.EpisodeEndingRepository;
import com.story.game.story.repository.EpisodeRepository;
import com.story.game.story.repository.StoryNodeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SequentialGenerationService {

    private final StoryCreationRepository storyCreationRepository;
    private final StoryDataRepository storyDataRepository;
    private final EpisodeRepository episodeRepository;
    private final StoryNodeRepository storyNodeRepository;
    private final EpisodeEndingRepository episodeEndingRepository;
    private final WebClient aiServerWebClient;
    private final ObjectMapper objectMapper;
    private final StoryMapper storyMapper;
    private final S3Service s3Service;
    private final SequentialGenerationService self;

    public SequentialGenerationService(
            StoryCreationRepository storyCreationRepository,
            StoryDataRepository storyDataRepository,
            EpisodeRepository episodeRepository,
            StoryNodeRepository storyNodeRepository,
            EpisodeEndingRepository episodeEndingRepository,
            WebClient aiServerWebClient,
            ObjectMapper objectMapper,
            StoryMapper storyMapper,
            S3Service s3Service,
            @org.springframework.context.annotation.Lazy SequentialGenerationService self) {
        this.storyCreationRepository = storyCreationRepository;
        this.storyDataRepository = storyDataRepository;
        this.episodeRepository = episodeRepository;
        this.storyNodeRepository = storyNodeRepository;
        this.episodeEndingRepository = episodeEndingRepository;
        this.aiServerWebClient = aiServerWebClient;
        this.objectMapper = objectMapper;
        this.storyMapper = storyMapper;
        this.s3Service = s3Service;
        this.self = self;
    }

    private final Map<String, StoryProgressResponseDto> generationTasks = new ConcurrentHashMap<>();

    public StoryProgressResponseDto getGenerationProgress(String taskId) {
        return generationTasks.getOrDefault(taskId, StoryProgressResponseDto.builder()
                .storyId(null)
                .status(null)
                .progress(StoryProgressResponseDto.ProgressData.builder()
                        .message("Task not found or already completed.")
                        .build())
                .build());
    }

    @Transactional
    public EpisodeDto startEpisodeGeneration(String storyId) {
        log.info("=== Start Sequential Episode Generation (EP 1) for storyId: {} ===", storyId);
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

        if (storyCreation.getStatus() != StoryCreation.CreationStatus.CONFIGURED) {
            throw new IllegalStateException("Cannot start generation: current status is " + storyCreation.getStatus());
        }

        storyCreation.setStatus(StoryCreation.CreationStatus.GENERATING);
        storyCreation.setCurrentPhase("GENERATING_EPISODE_1");
        storyCreation.setTotalEpisodesToGenerate(storyCreation.getNumEpisodes());
        storyCreationRepository.save(storyCreation);

        log.info("🔥 Calling runEpisodeGenerationTaskSync for Episode 1");
        EpisodeDto generatedEpisode = runEpisodeGenerationTaskSync(storyId, 1, null);
        log.info("✅ Episode 1 generation completed");

        return generatedEpisode;
    }

    @Transactional
    public EpisodeDto generateNextEpisode(String storyId) {
        log.info("=== Generate Next Episode for storyId: {} ===", storyId);
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

        int completedEpisodes = storyCreation.getCompletedEpisodes();
        int totalEpisodes = storyCreation.getTotalEpisodesToGenerate();

        if (completedEpisodes >= totalEpisodes) {
            throw new IllegalStateException("All episodes have already been generated.");
        }

        int nextEpisodeOrder = completedEpisodes + 1;

        Episode previousEpisodeEntity = episodeRepository.findByStoryAndOrder(storyCreation, completedEpisodes)
                .orElseThrow(() -> new IllegalStateException("Previous episode (order: " + completedEpisodes + ") not found in database."));

        EpisodeDto previousEpisodeDto = storyMapper.toEpisodeDto(previousEpisodeEntity);

        log.info("🔥 Calling runEpisodeGenerationTaskSync for Episode {}", nextEpisodeOrder);
        EpisodeDto generatedEpisode = runEpisodeGenerationTaskSync(storyId, nextEpisodeOrder, previousEpisodeDto);
        log.info("✅ Episode {} generation completed", nextEpisodeOrder);

        return generatedEpisode;
    }

    @Transactional
    public EpisodeDto runEpisodeGenerationTaskSync(String storyId, int episodeOrder, EpisodeDto previousEpisode) {
        log.info("🚀 [SYNC] runEpisodeGenerationTaskSync STARTED - storyId: {}, episodeOrder: {}", storyId, episodeOrder);
        StoryCreation storyCreation = null;
        try {
            storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

            int totalEpisodes = storyCreation.getTotalEpisodesToGenerate();
            log.info("📊 Total episodes to generate: {}", totalEpisodes);

            log.info("[LOG-STEP 1] Preparing AI request for episode {}", episodeOrder);
            GenerateNextEpisodeRequest aiRequest = prepareAiRequest(storyCreation, episodeOrder, previousEpisode);
            log.info("[LOG-STEP 2] AI request prepared. Calling AI server...");

            log.info("=== AI Request for Episode {} ===", episodeOrder);
            log.info("📤 Sending AI request to: /generate-next-episode");
            log.info("📦 Request payload - episodeOrder: {}, has previousEpisode: {}", episodeOrder, previousEpisode != null);

            EpisodeDto newEpisodeDto = aiServerWebClient.post()
                    .uri("/generate-next-episode")
                    .bodyValue(aiRequest)
                    .retrieve()
                    .bodyToMono(EpisodeDto.class)
                    .block();
            
            log.info("[LOG-STEP 3] AI response received.");

            if (newEpisodeDto == null) {
                log.error("[LOG-FAIL] AI server returned null DTO.");
                throw new RuntimeException("AI server returned no data for the new episode.");
            }
            log.info("✅ AI Response received - Episode title: {}", newEpisodeDto.getTitle());
            log.info("📝 Intro text present: {} (length: {})",
                newEpisodeDto.getIntroText() != null,
                newEpisodeDto.getIntroText() != null ? newEpisodeDto.getIntroText().length() : 0);
            log.info("[LOG-STEP 4] AI DTO is valid. Proceeding to save to DB...");

            // 1. Save the new episode to the database
            storyMapper.saveEpisodeDtoToDb(newEpisodeDto, storyCreation);
            
            log.info("[LOG-STEP 5] saveEpisodeDtoToDb completed. Fetching new entity...");

            // Fetch the episode entity we just saved
            Episode newEpisodeEntity = episodeRepository.findByStoryAndOrder(storyCreation, episodeOrder)
                .orElseThrow(() -> new RuntimeException("Failed to fetch the newly created episode for order: " + episodeOrder));

            log.info("[LOG-STEP 6] New episode entity fetched. Processing episode endings...");
            // Save the episode endings provided by the AI
            if (newEpisodeDto.getEndings() != null && !newEpisodeDto.getEndings().isEmpty()) {
                log.info("Saving {} episode endings for episode order {}", newEpisodeDto.getEndings().size(), episodeOrder);
                for (com.story.game.common.dto.EpisodeEndingDto endingDto : newEpisodeDto.getEndings()) {
                    com.story.game.story.entity.EpisodeEnding endingEntity = new com.story.game.story.entity.EpisodeEnding();
                    endingEntity.setEpisode(newEpisodeEntity);
                    endingEntity.setTitle(endingDto.getTitle());
                    endingEntity.setText(endingDto.getText());
                    endingEntity.setCondition(endingDto.getCondition());

                    if (endingDto.getGaugeChanges() != null) {
                        try {
                            endingEntity.setGaugeChanges(objectMapper.writeValueAsString(endingDto.getGaugeChanges()));
                        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                            log.error("Failed to serialize gauge changes for episode ending", e);
                        }
                    }

                    episodeEndingRepository.save(endingEntity);
                }
            }
            log.info("[LOG-STEP 7] Episode endings processed. Uploading snapshot to S3...");

            // 2. Create a JSON snapshot and upload to S3
            FullStoryDto fullStoryForS3 = storyMapper.buildFullStoryDtoFromDb(storyCreation);
            String storyFileKey = "stories/" + storyId + ".json";
            s3Service.uploadFile(storyFileKey, objectMapper.writeValueAsString(fullStoryForS3));
            storyCreation.setS3FileKey(storyFileKey);

            log.info("[LOG-STEP 8] S3 upload complete. Updating progress...");

            // 3. Update progress
            storyCreation.setCompletedEpisodes(episodeOrder);
            int overallProgress = (int) (((double) episodeOrder / totalEpisodes) * 100);
            storyCreation.setProgressPercentage(overallProgress);

            if (episodeOrder == totalEpisodes) {
                storyCreation.setStatus(StoryCreation.CreationStatus.COMPLETED);
                storyCreation.setCompletedAt(LocalDateTime.now());
                storyCreation.setCurrentPhase("COMPLETED");

                // Create the final StoryData entity for gameplay
                StoryData storyData = StoryData.builder()
                    .title(storyCreation.getTitle())
                    .description(storyCreation.getDescription())
                    .storyFileKey(storyCreation.getS3FileKey())
                    .totalEpisodes(episodeRepository.findByStoryAndOrder(storyCreation, totalEpisodes).map(e -> e.getOrder()).orElse(0))
                    .totalNodes((int) storyNodeRepository.countByEpisode_Story(storyCreation))
                    .build();
                storyDataRepository.save(storyData);
                storyCreation.setStoryDataId(storyData.getId());
            } else {
                storyCreation.setStatus(StoryCreation.CreationStatus.AWAITING_USER_ACTION);
                storyCreation.setCurrentPhase("AWAITING_NEXT_EPISODE_TRIGGER");
            }
            storyCreationRepository.save(storyCreation);

            log.info("[LOG-STEP 9] Progress updated. Task finished successfully.");
            return newEpisodeDto;

        } catch (Exception e) {
            log.error("Episode generation task failed for storyId: {}", storyId, e);
            if (storyCreation != null) {
                storyCreation.setStatus(StoryCreation.CreationStatus.FAILED);
                storyCreation.setErrorMessage(e.getMessage());
                storyCreationRepository.save(storyCreation);
            }
            throw new RuntimeException("Episode generation failed: " + e.getMessage(), e);
        }
    }


    @Builder
    private static class GenerateNextEpisodeRequest {
        public InitialAnalysis initialAnalysis;
        public StoryConfig storyConfig;
        public String novelContext;
        public int currentEpisodeOrder;
        public EpisodeDto previousEpisode;
    }

    @Builder
    private static class InitialAnalysis {
        public String summary;
        public List<CharacterDto> characters;
    }

    @Builder
    private static class StoryConfig {
        public int numEpisodes;
        public int maxDepth;
        public List<String> selectedGaugeIds;
    }

    private GenerateNextEpisodeRequest prepareAiRequest(StoryCreation storyCreation, int episodeOrder, EpisodeDto previousEpisode) throws IOException {
        List<CharacterDto> characters = new ArrayList<>();
        if (storyCreation.getCharactersJson() != null && !storyCreation.getCharactersJson().isBlank()) {
            characters = objectMapper.readValue(storyCreation.getCharactersJson(), new TypeReference<>() {});
        }
        List<String> selectedGauges = new ArrayList<>();
        if (storyCreation.getSelectedGaugeIdsJson() != null && !storyCreation.getSelectedGaugeIdsJson().isBlank()) {
            selectedGauges = objectMapper.readValue(storyCreation.getSelectedGaugeIdsJson(), new TypeReference<>() {});
        }
        InitialAnalysis initialAnalysis = InitialAnalysis.builder()
                .summary(storyCreation.getSummary())
                .characters(characters)
                .build();
        StoryConfig storyConfig = StoryConfig.builder()
                .numEpisodes(storyCreation.getNumEpisodes())
                .maxDepth(storyCreation.getMaxDepth())
                .selectedGaugeIds(selectedGauges)
                .build();
        return GenerateNextEpisodeRequest.builder()
                .initialAnalysis(initialAnalysis)
                .storyConfig(storyConfig)
                .novelContext(storyCreation.getNovelText() != null ? storyCreation.getNovelText() : "")
                .currentEpisodeOrder(episodeOrder)
                .previousEpisode(previousEpisode)
                .build();
    }

    public boolean checkAiServerHealth() {
        try {
            String response = aiServerWebClient.get()
                .uri("/health") // Assuming AI server has a /health endpoint
                .retrieve()
                .bodyToMono(String.class)
                .block();
            return response != null;
        } catch (Exception e) {
            log.warn("AI server health check failed: {}", e.getMessage());
            return false;
        }
    }

    private void updateProgress(String taskId, String storyId, StoryCreation.CreationStatus status, String message, int percentage, int completedEp, int totalEp, String error) {
        // 현재까지 생성된 에피소드 목록 조회
        List<EpisodeDto> episodes = new ArrayList<>();
        try {
            StoryCreation storyCreation = storyCreationRepository.findById(storyId).orElse(null);
            if (storyCreation != null) {
                List<Episode> episodeEntities = episodeRepository.findAllByStoryOrderByOrderAsc(storyCreation);
                episodes = episodeEntities.stream()
                        .map(storyMapper::toEpisodeDto)
                        .collect(Collectors.toList());
                log.info("📦 Progress update - found {} episodes for storyId: {}", episodes.size(), storyId);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to fetch episodes for progress update: {}", e.getMessage());
        }

        StoryProgressResponseDto.ProgressData progressData = StoryProgressResponseDto.ProgressData.builder()
                .currentPhase(status.toString())
                .completedEpisodes(completedEp)
                .totalEpisodes(totalEp)
                .percentage(percentage)
                .message(message)
                .error(error)
                .build();
        StoryProgressResponseDto progressDto = StoryProgressResponseDto.builder()
                .storyId(storyId)
                .status(status)
                .progress(progressData)
                .episodes(episodes)  // 생성된 에피소드 목록 포함
                .build();
        generationTasks.put(taskId, progressDto);
    }
}
