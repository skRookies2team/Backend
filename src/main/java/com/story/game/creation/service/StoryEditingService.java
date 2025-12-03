package com.story.game.creation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.EpisodeDto;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.StoryChoiceDto;
import com.story.game.common.dto.StoryNodeDto;
import com.story.game.creation.dto.RegenerateProgressDto;
import com.story.game.creation.dto.RegenerateSubtreeResponseDto;
import com.story.game.creation.dto.SubtreeRegenerationRequestDto;
import com.story.game.creation.dto.TaskStartResponseDto;
import com.story.game.creation.dto.UpdateNodeRequestDto;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.infrastructure.s3.S3Service;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StoryEditingService {

    private final StoryCreationRepository storyCreationRepository;
    private final S3Service s3Service;
    private final WebClient relayServerWebClient;
    private final ObjectMapper objectMapper;

    private final Map<String, RegenerateProgressDto> taskProgress = new ConcurrentHashMap<>();

    public RegenerateProgressDto getRegenerateProgress(String taskId) {
        return taskProgress.getOrDefault(taskId,
                RegenerateProgressDto.builder()
                        .taskId(taskId)
                        .status("not_found")
                        .message("Task not found or already completed.")
                        .progress(0)
                        .build());
    }

    public TaskStartResponseDto startRegenerateSubtreeAsync(
            String storyId, Integer episodeOrder, String nodeId, UpdateNodeRequestDto requestDto) {
        
        final String taskId = UUID.randomUUID().toString();
        
        RegenerateProgressDto initialProgress = RegenerateProgressDto.builder()
                .taskId(taskId)
                .status("pending")
                .message("Regeneration task has been queued.")
                .progress(0)
                .build();
        taskProgress.put(taskId, initialProgress);

        runRegenerationTask(taskId, storyId, episodeOrder, nodeId, requestDto);

        return new TaskStartResponseDto(taskId);
    }

    @Async
    @Transactional
    public void runRegenerationTask(String taskId, String storyId, Integer episodeOrder, String nodeId, UpdateNodeRequestDto requestDto) {
        try {
            updateProgress(taskId, "in_progress", "Fetching story data from S3...", 10, null);
            
            StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                    .orElseThrow(() -> new EntityNotFoundException("StoryCreation not found with id: " + storyId));

            String storyJson = s3Service.downloadFileContent(storyCreation.getS3FileKey());
            FullStoryDto fullStory = objectMapper.readValue(storyJson, FullStoryDto.class);

            updateProgress(taskId, "in_progress", "Finding and updating the target node...", 30, null);

            StoryNodeDto parentNodeToRegenerate = findAndUpdateNode(fullStory, episodeOrder, nodeId, requestDto);

            updateProgress(taskId, "in_progress", "Preparing data for AI server...", 50, null);

            SubtreeRegenerationRequestDto aiRequest = buildAiRequest(storyCreation, fullStory, parentNodeToRegenerate, episodeOrder);

            updateProgress(taskId, "in_progress", "Calling AI server for regeneration...", 60, null);

            List<StoryNodeDto> regeneratedChildren = callAiRegenerationApi(aiRequest).block();

            updateProgress(taskId, "in_progress", "Replacing subtree and saving to S3...", 80, null);

            // Replace subtree: Support both flat and tree structures
            EpisodeDto targetEpisode = fullStory.getEpisodes().stream()
                    .filter(e -> e.getOrder().equals(episodeOrder))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Episode not found"));

            // 1. Remove old child nodes from flat structure (episode.nodes)
            if (parentNodeToRegenerate.getChildren() != null && !parentNodeToRegenerate.getChildren().isEmpty()) {
                // Collect all descendant IDs to remove
                List<String> descendantIds = collectAllDescendantIds(parentNodeToRegenerate.getChildren());
                targetEpisode.getNodes().removeIf(node -> descendantIds.contains(node.getId()));
                log.info("Removed {} old descendant nodes from episode.nodes", descendantIds.size());
            }

            // 2. Update tree structure (parent.children)
            if (parentNodeToRegenerate.getChildren() == null) {
                parentNodeToRegenerate.setChildren(new ArrayList<>());
            }
            parentNodeToRegenerate.getChildren().clear();
            if (regeneratedChildren != null) {
                parentNodeToRegenerate.getChildren().addAll(regeneratedChildren);

                // 3. Add new nodes to flat structure (episode.nodes)
                List<StoryNodeDto> allNewNodes = collectAllNodes(regeneratedChildren);
                targetEpisode.getNodes().addAll(allNewNodes);
                log.info("Added {} new nodes to episode.nodes", allNewNodes.size());
            }
            
            String updatedStoryJson = objectMapper.writeValueAsString(fullStory);
            s3Service.uploadFile(storyCreation.getS3FileKey(), updatedStoryJson);

            updateProgress(taskId, "completed", "Subtree regenerated successfully.", 100, regeneratedChildren);
            
        } catch (Exception e) {
            log.error("Error during subtree regeneration for taskId {}: {}", taskId, e.getMessage(), e);
            updateProgress(taskId, "failed", e.getMessage(), 0, null);
        }
    }

    private StoryNodeDto findAndUpdateNode(FullStoryDto fullStory, Integer episodeOrder, String nodeId, UpdateNodeRequestDto requestDto) {
        EpisodeDto episode = fullStory.getEpisodes().stream()
                .filter(e -> e.getOrder().equals(episodeOrder))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Episode not found with order: " + episodeOrder));

        StoryNodeDto targetNode = episode.getNodes().stream()
                .filter(n -> n.getId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Node not found with id: " + nodeId));
        
        targetNode.setText(requestDto.getNodeText());
        // Convert List<String> to List<StoryChoiceDto>
        if (requestDto.getChoices() != null) {
            List<StoryChoiceDto> choiceDtos = requestDto.getChoices().stream()
                    .map(text -> StoryChoiceDto.builder().text(text).build())
                    .collect(Collectors.toList());
            targetNode.setChoices(choiceDtos);
        }
        targetNode.getDetails().setSituation(requestDto.getSituation());
        targetNode.getDetails().setNpcEmotions(requestDto.getNpcEmotions());
        // 'tags' do not exist on StoryNodeDetailDto, so we ignore it.

        return targetNode;
    }
    
    private SubtreeRegenerationRequestDto buildAiRequest(StoryCreation storyCreation, FullStoryDto fullStory, StoryNodeDto parentNode, int episodeOrder) throws IOException {
        if (episodeOrder < 1 || episodeOrder > fullStory.getEpisodes().size()) {
            throw new IllegalArgumentException("Invalid episode order: " + episodeOrder + ". Must be between 1 and " + fullStory.getEpisodes().size());
        }
        EpisodeDto episode = fullStory.getEpisodes().get(episodeOrder - 1);
        
        List<String> choiceTexts = parentNode.getChoices().stream()
                                             .map(StoryChoiceDto::getText)
                                             .collect(Collectors.toList());

        List<String> selectedGaugeIds = objectMapper.readValue(storyCreation.getSelectedGaugeIdsJson(), new TypeReference<>() {});

        SubtreeRegenerationRequestDto.ParentNodeDto parentNodeDto = SubtreeRegenerationRequestDto.ParentNodeDto.builder()
                .nodeId(parentNode.getId())
                .text(parentNode.getText())
                .choices(choiceTexts)
                .situation(parentNode.getDetails().getSituation())
                .npcEmotions(parentNode.getDetails().getNpcEmotions())
                // .tags(...) 'tags' do not exist
                .depth(parentNode.getDepth())
                .build();

        return SubtreeRegenerationRequestDto.builder()
                .episodeTitle(episode.getTitle())
                .episodeOrder(episodeOrder)
                .parentNode(parentNodeDto)
                .currentDepth(parentNode.getDepth())
                .maxDepth(storyCreation.getMaxDepth())
                .novelContext(storyCreation.getNovelText())
                .previousChoices(List.of()) // This might need more sophisticated logic
                .selectedGaugeIds(selectedGaugeIds)
                .build();
    }

    private Mono<List<StoryNodeDto>> callAiRegenerationApi(SubtreeRegenerationRequestDto aiRequest) {
        return relayServerWebClient.post()
                .uri("/ai/regenerate-subtree")
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(RegenerateSubtreeResponseDto.class)
                .map(RegenerateSubtreeResponseDto::getRegeneratedNodes)
                .doOnError(e -> log.error("AI server call failed: {}", e.getMessage()));
    }

    private void updateProgress(String taskId, String status, String message, int progress, List<StoryNodeDto> nodes) {
        RegenerateProgressDto progressDto = RegenerateProgressDto.builder()
                .taskId(taskId)
                .status(status)
                .message(message)
                .progress(progress)
                .regeneratedNodes(nodes)
                .build();
        taskProgress.put(taskId, progressDto);
    }

    /**
     * Recursively collect all descendant node IDs from a list of nodes
     */
    private List<String> collectAllDescendantIds(List<StoryNodeDto> nodes) {
        List<String> ids = new ArrayList<>();
        if (nodes == null) {
            return ids;
        }
        for (StoryNodeDto node : nodes) {
            ids.add(node.getId());
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                ids.addAll(collectAllDescendantIds(node.getChildren()));
            }
        }
        return ids;
    }

    /**
     * Recursively collect all nodes (including descendants) from a list of root nodes
     */
    private List<StoryNodeDto> collectAllNodes(List<StoryNodeDto> nodes) {
        List<StoryNodeDto> allNodes = new ArrayList<>();
        if (nodes == null) {
            return allNodes;
        }
        for (StoryNodeDto node : nodes) {
            allNodes.add(node);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                allNodes.addAll(collectAllNodes(node.getChildren()));
            }
        }
        return allNodes;
    }
}
