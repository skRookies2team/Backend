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

@Service
@RequiredArgsConstructor
@Slf4j
public class SequentialGenerationService {

    private final StoryCreationRepository storyCreationRepository;
    private final StoryDataRepository storyDataRepository;
    private final WebClient aiServerWebClient;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

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
    public TaskStartResponseDto startEpisodeGeneration(String storyId) {
        log.info("=== Start Sequential Episode Generation (EP 1) for storyId: {} ===", storyId);
        final String taskId = UUID.randomUUID().toString();
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

        if (storyCreation.getStatus() != StoryCreation.CreationStatus.CONFIGURED) {
            throw new IllegalStateException("Cannot start generation: current status is " + storyCreation.getStatus());
        }

        storyCreation.setStatus(StoryCreation.CreationStatus.GENERATING);
        storyCreation.setCurrentPhase("GENERATING_EPISODE_1");
        storyCreation.setTotalEpisodesToGenerate(storyCreation.getNumEpisodes());
        storyCreationRepository.save(storyCreation);

        updateProgress(taskId, storyId, StoryCreation.CreationStatus.GENERATING, "Generation task for Episode 1 has been queued.", 0, 0, storyCreation.getNumEpisodes(), null);

        runEpisodeGenerationTask(taskId, storyId, 1, null);

        return new TaskStartResponseDto(taskId);
    }

    @Transactional
    public TaskStartResponseDto generateNextEpisode(String storyId) {
        log.info("=== Generate Next Episode for storyId: {} ===", storyId);
        final String taskId = UUID.randomUUID().toString();
        StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

        int completedEpisodes = storyCreation.getCompletedEpisodes();
        int totalEpisodes = storyCreation.getTotalEpisodesToGenerate();

        if (completedEpisodes >= totalEpisodes) {
            throw new IllegalStateException("All episodes have already been generated.");
        }
        if (storyCreation.getS3FileKey() == null || storyCreation.getS3FileKey().isBlank()) {
            throw new IllegalStateException("Cannot generate next episode, initial story file does not exist.");
        }

        int nextEpisodeOrder = completedEpisodes + 1;

        updateProgress(taskId, storyId, StoryCreation.CreationStatus.GENERATING, "Task for Episode " + nextEpisodeOrder + " queued.", 0, completedEpisodes, totalEpisodes, null);

        try {
            String storyJson = s3Service.downloadFileContent(storyCreation.getS3FileKey());
            FullStoryDto partialStory = objectMapper.readValue(storyJson, FullStoryDto.class);
            
            EpisodeDto previousEpisode = partialStory.getEpisodes().stream()
                    .filter(e -> e.getOrder().equals(completedEpisodes))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Previous episode not found in S3 file."));

            runEpisodeGenerationTask(taskId, storyId, nextEpisodeOrder, previousEpisode);
        } catch (Exception e) {
            log.error("Failed to prepare for next episode generation", e);
            throw new RuntimeException("Failed to load previous episode data.", e);
        }

        return new TaskStartResponseDto(taskId);
    }

    @Async
    @Transactional
    public void runEpisodeGenerationTask(String taskId, String storyId, int episodeOrder, EpisodeDto previousEpisode) {
        StoryCreation storyCreation = null;
        try {
            storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new EntityNotFoundException("Story not found: " + storyId));

            int totalEpisodes = storyCreation.getTotalEpisodesToGenerate();
            updateProgress(taskId, storyId, StoryCreation.CreationStatus.GENERATING, "Calling AI for Episode " + episodeOrder + "...", 10, episodeOrder - 1, totalEpisodes, null);

            GenerateNextEpisodeRequest aiRequest = prepareAiRequest(storyCreation, episodeOrder, previousEpisode);

            EpisodeDto newEpisode = aiServerWebClient.post()
                    .uri("/generate-next-episode")
                    .bodyValue(aiRequest)
                    .retrieve()
                    .bodyToMono(EpisodeDto.class)
                    .block();

            if (newEpisode == null) {
                throw new RuntimeException("AI server returned no data for the new episode.");
            }
            updateProgress(taskId, storyId, StoryCreation.CreationStatus.GENERATING, "AI completed Episode " + episodeOrder + ". Saving...", 70, episodeOrder - 1, totalEpisodes, null);

            if (episodeOrder == 1) {
                FullStoryDto fullStory = new FullStoryDto();
                fullStory.setEpisodes(new ArrayList<>(List.of(newEpisode)));
                String storyFileKey = "stories/" + storyId + ".json";
                s3Service.uploadFile(storyFileKey, objectMapper.writeValueAsString(fullStory));
                storyCreation.setS3FileKey(storyFileKey);
            } else {
                String storyJson = s3Service.downloadFileContent(storyCreation.getS3FileKey());
                FullStoryDto fullStory = objectMapper.readValue(storyJson, FullStoryDto.class);
                fullStory.getEpisodes().add(newEpisode);
                s3Service.uploadFile(storyCreation.getS3FileKey(), objectMapper.writeValueAsString(fullStory));
            }

            storyCreation.setCompletedEpisodes(episodeOrder);
            int overallProgress = (int) (((double) episodeOrder / totalEpisodes) * 100);
            storyCreation.setProgressPercentage(overallProgress);

            if (episodeOrder == totalEpisodes) {
                storyCreation.setStatus(StoryCreation.CreationStatus.COMPLETED);
                storyCreation.setCompletedAt(LocalDateTime.now());
                storyCreation.setCurrentPhase("COMPLETED");

                String finalStoryJson = s3Service.downloadFileContent(storyCreation.getS3FileKey());
                FullStoryDto finalStory = objectMapper.readValue(finalStoryJson, FullStoryDto.class);
                
                StoryData storyData = StoryData.builder()
                    .title(storyCreation.getTitle())
                    .description(storyCreation.getDescription())
                    .storyFileKey(storyCreation.getS3FileKey())
                    .totalEpisodes(finalStory.getEpisodes().size())
                    .totalNodes(finalStory.getEpisodes().stream().mapToInt(ep -> countNodes(ep.getNodes().get(0))).sum())
                    .build();
                storyDataRepository.save(storyData);
                storyCreation.setStoryDataId(storyData.getId());

                updateProgress(taskId, storyId, StoryCreation.CreationStatus.COMPLETED, "All episodes generated successfully.", 100, totalEpisodes, totalEpisodes, null);
            } else {
                storyCreation.setStatus(StoryCreation.CreationStatus.GENERATING);
                storyCreation.setCurrentPhase("AWAITING_NEXT_EPISODE");
                 updateProgress(taskId, storyId, StoryCreation.CreationStatus.GENERATING, "Episode " + episodeOrder + " completed. Ready for next.", 100, episodeOrder, totalEpisodes, null);
            }
            storyCreationRepository.save(storyCreation);
        } catch (Exception e) {
            log.error("Episode generation task failed for storyId: {}", storyId, e);
            if (storyCreation != null) {
                storyCreation.setStatus(StoryCreation.CreationStatus.FAILED);
                storyCreation.setErrorMessage(e.getMessage());
                storyCreationRepository.save(storyCreation);
            }
             updateProgress(taskId, storyId, StoryCreation.CreationStatus.FAILED, e.getMessage(), 0,
                    storyCreation != null ? storyCreation.getCompletedEpisodes() : 0, 
                    storyCreation != null ? storyCreation.getTotalEpisodesToGenerate() : 0, 
                    e.getMessage());
        }
    }

    private int countNodes(StoryNodeDto node) {
        if (node == null) return 0;
        int count = 1;
        if (node.getChildren() != null) {
            for(StoryNodeDto child : node.getChildren()) {
                count += countNodes(child);
            }
        }
        return count;
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

    private void updateProgress(String taskId, String storyId, StoryCreation.CreationStatus status, String message, int percentage, int completedEp, int totalEp, String error) {
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
                .build();
        generationTasks.put(taskId, progressDto);
    }
}
