package com.story.game.creation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.StoryNodeDto;
import com.story.game.creation.dto.RegenerateSubtreeResponseDto;
import com.story.game.creation.dto.SubtreeRegenerationRequestDto;
import com.story.game.creation.dto.UpdateNodeRequestDto;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.infrastructure.s3.S3Service;
import com.story.game.story.entity.Episode;
import com.story.game.story.entity.StoryNode;
import com.story.game.story.mapper.StoryMapper;
import com.story.game.story.repository.EpisodeRepository;
import com.story.game.story.repository.StoryNodeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class StoryEditingService {

    private final StoryCreationRepository storyCreationRepository;
    private final EpisodeRepository episodeRepository;
    private final StoryNodeRepository storyNodeRepository;
    private final S3Service s3Service; // Kept for snapshot updates
    private final WebClient relayServerWebClient;
    private final ObjectMapper objectMapper;
    private final StoryMapper storyMapper;

    @Transactional
    public List<StoryNodeDto> regenerateSubtreeSync(
            String storyId, String nodeId, UpdateNodeRequestDto requestDto) {

        log.info("=== Regenerate Subtree Synchronously ===");
        log.info("StoryId: {}, NodeId: {}", storyId, nodeId);

        try {
            UUID nodeUUID = UUID.fromString(nodeId);
            StoryNode sourceNode = storyNodeRepository.findById(nodeUUID)
                    .orElseThrow(() -> new EntityNotFoundException("StoryNode not found with id: " + nodeId));

            log.info("✅ Found node: {}", sourceNode.getId());

            // Update the source node text
            sourceNode.setText(requestDto.getNodeText());

            // Clear the children using orphanRemoval
            sourceNode.getOutgoingChoices().clear();
            storyNodeRepository.saveAndFlush(sourceNode);

            log.info("🗑️ Cleared existing children");

            Episode episode = sourceNode.getEpisode();
            StoryCreation storyCreation = episode.getStory();
            SubtreeRegenerationRequestDto aiRequest = buildAiRequest(storyCreation, episode, sourceNode, requestDto);

            log.info("📤 Calling AI server for subtree regeneration...");
            log.info("Request summary present: {}", aiRequest.getSummary() != null);
            log.info("Request charactersJson present: {}", aiRequest.getCharactersJson() != null);
            log.info("Request gaugesJson present: {}", aiRequest.getGaugesJson() != null);
            log.info("Request novelContext present: {}, length: {}",
                aiRequest.getNovelContext() != null,
                aiRequest.getNovelContext() != null ? aiRequest.getNovelContext().length() : 0);
            log.info("StoryCreation novelText present: {}, length: {}",
                storyCreation.getNovelText() != null,
                storyCreation.getNovelText() != null ? storyCreation.getNovelText().length() : 0);

            List<StoryNodeDto> regeneratedChildren = callAiRegenerationApi(aiRequest).block();

            log.info("✅ AI response received");

            // Attach new children
            if (regeneratedChildren != null) {
                storyMapper.addChildrenToNode(sourceNode, regeneratedChildren);
                log.info("📦 Attached {} new child nodes", regeneratedChildren.size());
            }
            storyNodeRepository.save(sourceNode);

            // Update S3 snapshot
            FullStoryDto fullStoryForS3 = storyMapper.buildFullStoryDtoFromDb(storyCreation);
            s3Service.uploadFile(storyCreation.getS3FileKey(), objectMapper.writeValueAsString(fullStoryForS3));

            log.info("💾 Updated S3 snapshot");
            log.info("✅ Subtree regeneration completed successfully");

            return regeneratedChildren;

        } catch (Exception e) {
            log.error("❌ Error during subtree regeneration: {}", e.getMessage(), e);
            throw new RuntimeException("Subtree regeneration failed: " + e.getMessage(), e);
        }
    }
    
    private SubtreeRegenerationRequestDto buildAiRequest(StoryCreation storyCreation, Episode episode, StoryNode parentNode, UpdateNodeRequestDto requestDto) throws IOException {
        
        List<String> selectedGaugeIds = objectMapper.readValue(storyCreation.getSelectedGaugeIdsJson(), new TypeReference<>() {});

        SubtreeRegenerationRequestDto.ParentNodeDto parentNodeDto = SubtreeRegenerationRequestDto.ParentNodeDto.builder()
                .nodeId(parentNode.getId().toString())
                .text(requestDto.getNodeText()) // Use the updated text
                .choices(requestDto.getChoices())
                .situation(requestDto.getSituation())
                .npcEmotions(requestDto.getNpcEmotions())
                .depth(parentNode.getDepth())
                .build();

        return SubtreeRegenerationRequestDto.builder()
                .episodeTitle(episode.getTitle())
                .episodeOrder(episode.getOrder())
                .parentNode(parentNodeDto)
                .currentDepth(parentNode.getDepth())
                .maxDepth(storyCreation.getMaxDepth())
                .novelContext(storyCreation.getNovelText())
                .previousChoices(List.of()) // This might need more sophisticated logic
                .selectedGaugeIds(selectedGaugeIds)
                // Add cached analysis data for performance optimization
                .summary(storyCreation.getSummary())
                .charactersJson(storyCreation.getCharactersJson())
                .gaugesJson(storyCreation.getGaugesJson())
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
}
