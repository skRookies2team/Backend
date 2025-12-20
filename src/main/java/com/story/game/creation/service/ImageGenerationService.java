package com.story.game.creation.service;

import com.story.game.ai.dto.ImageGenerationRequestDto;
import com.story.game.ai.dto.ImageGenerationResponseDto;
import com.story.game.ai.service.RelayServerClient;
import com.story.game.story.entity.StoryNode;
import com.story.game.story.repository.StoryNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationService {

    private final RelayServerClient relayServerClient;
    private final StoryNodeRepository storyNodeRepository;

    @Value("${aws.s3.bucket}")
    private String s3BucketName;

    /**
     * Determine if a node should have an auto-generated image
     * Rule: Root nodes (depth=0) and ending nodes
     */
    public boolean shouldGenerateImage(StoryNode node) {
        // Root nodes
        if (node.getDepth() != null && node.getDepth() == 0) {
            return true;
        }

        // Ending nodes (no child choices)
        if (node.getOutgoingChoices() == null || node.getOutgoingChoices().isEmpty()) {
            return true;
        }

        return false;
    }

    /**
     * Generate and save image for a node
     * Synchronous processing as requested
     */
    @Transactional
    public void generateAndSaveNodeImage(
        String storyCreationId,
        StoryNode node,
        String episodeTitle,
        Integer episodeOrder
    ) {
        if (!shouldGenerateImage(node)) {
            log.debug("Node {} does not require image generation", node.getId());
            return;
        }

        log.info("Generating image for node {} (depth={}, type={})",
            node.getId(), node.getDepth(), node.getNodeType());

        try {
            // Build request
            ImageGenerationRequestDto request = ImageGenerationRequestDto.builder()
                .storyId(storyCreationId)
                .nodeId(node.getId().toString())
                .nodeText(node.getText())
                .situation(node.getSituation())
                .episodeTitle(episodeTitle)
                .episodeOrder(episodeOrder)
                .nodeDepth(node.getDepth())
                .novelS3Bucket(s3BucketName)
                .novelS3Key("novels/original/" + storyCreationId + ".txt")
                .build();

            // Call relay server (synchronous)
            ImageGenerationResponseDto response = relayServerClient.generateImage(request);

            // Update node with image info
            node.setImageUrl(response.getImageUrl());
            node.setImageFileKey(response.getFileKey());
            storyNodeRepository.save(node);

            log.info("Image generated and saved for node {}: {}",
                node.getId(), response.getImageUrl());

        } catch (Exception e) {
            log.error("Failed to generate image for node {}: {}",
                node.getId(), e.getMessage(), e);
            // Don't fail the entire story generation if image fails
        }
    }
}
