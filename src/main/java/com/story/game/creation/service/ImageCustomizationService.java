package com.story.game.creation.service;

import com.story.game.ai.dto.ImageGenerationRequestDto;
import com.story.game.ai.dto.ImageGenerationResponseDto;
import com.story.game.ai.service.RelayServerClient;
import com.story.game.creation.dto.NodeImageResponseDto;
import com.story.game.creation.dto.RegenerateImageRequestDto;
import com.story.game.creation.dto.UploadImageRequestDto;
import com.story.game.creation.dto.UploadImageResponseDto;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.infrastructure.s3.S3Service;
import com.story.game.story.entity.Episode;
import com.story.game.story.entity.StoryNode;
import com.story.game.story.repository.EpisodeRepository;
import com.story.game.story.repository.StoryNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageCustomizationService {

    private final StoryNodeRepository storyNodeRepository;
    private final StoryCreationRepository storyCreationRepository;
    private final EpisodeRepository episodeRepository;
    private final RelayServerClient relayServerClient;
    private final S3Service s3Service;

    @Value("${aws.s3.bucket}")
    private String s3BucketName;

    /**
     * Regenerate image with custom prompt for any episode nodes
     */
    @Transactional
    public ImageGenerationResponseDto regenerateImage(
        String storyCreationId,
        String nodeId,
        RegenerateImageRequestDto request
    ) {
        // Validate node exists
        StoryNode node = validateNode(storyCreationId, nodeId);
        Episode episode = node.getEpisode();

        // Build request with custom prompt
        ImageGenerationRequestDto imageRequest = ImageGenerationRequestDto.builder()
            .storyId(storyCreationId)
            .nodeId(nodeId)
            .nodeText(request.getCustomPrompt())  // Use custom prompt
            .episodeTitle(episode.getTitle())
            .episodeOrder(episode.getOrder())
            .nodeDepth(node.getDepth())
            .novelS3Bucket(s3BucketName)
            .novelS3Key("novels/original/" + storyCreationId + ".txt")
            .build();

        // Generate via relay server
        ImageGenerationResponseDto response = relayServerClient.generateImage(imageRequest);

        // Update node
        node.setImageUrl(response.getImageUrl());
        node.setImageFileKey(response.getFileKey());
        storyNodeRepository.save(node);

        return response;
    }

    /**
     * Upload user's custom image for any episode nodes
     */
    @Transactional
    public UploadImageResponseDto uploadCustomImage(
        String storyCreationId,
        String nodeId,
        UploadImageRequestDto request
    ) {
        // Validate node exists
        StoryNode node = validateNode(storyCreationId, nodeId);

        // Generate presigned upload URL
        String fileKey = "images/custom/" + storyCreationId + "/nodes/" + nodeId + ".jpg";
        S3Service.PresignedUrlInfo presignedUrlInfo =
            s3Service.generatePresignedUploadUrl(fileKey);

        // Prepare response
        UploadImageResponseDto response = UploadImageResponseDto.builder()
            .uploadUrl(presignedUrlInfo.getUrl())
            .fileKey(fileKey)
            .expiresIn(900) // 15 minutes
            .build();

        // Update node with file key (URL will be set after upload confirmation)
        node.setImageFileKey(fileKey);
        storyNodeRepository.save(node);

        return response;
    }

    /**
     * Get current image for a node
     */
    public NodeImageResponseDto getNodeImage(String storyCreationId, String nodeId) {
        StoryNode node = storyNodeRepository.findById(UUID.fromString(nodeId))
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));

        String presignedUrl = null;
        if (node.getImageFileKey() != null) {
            presignedUrl = s3Service.generatePresignedDownloadUrl(node.getImageFileKey());
        }

        return NodeImageResponseDto.builder()
            .nodeId(nodeId)
            .imageUrl(presignedUrl)
            .imageFileKey(node.getImageFileKey())
            .build();
    }

    /**
     * Validate that the node exists
     * Returns the node if valid, throws exception otherwise
     */
    private StoryNode validateNode(String storyCreationId, String nodeId) {
        StoryNode node = storyNodeRepository.findById(UUID.fromString(nodeId))
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));

        Episode episode = node.getEpisode();
        if (episode == null) {
            throw new IllegalArgumentException("Episode not found for node: " + nodeId);
        }

        // Allow custom images for all episodes
        log.info("Custom image request for Episode {} node {}", episode.getOrder(), nodeId);

        return node;
    }
}
