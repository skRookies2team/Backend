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
     * Generate image with custom prompt for any episode nodes
     */
    @Transactional
    public ImageGenerationResponseDto generateNodeImage(
        String storyId,
        String nodeId,
        RegenerateImageRequestDto request
    ) {
        // Validate node exists
        StoryNode node = validateNode(storyId, nodeId);
        Episode episode = node.getEpisode();

        // Generate S3 presigned URL for image upload
        String imageKey = "images/custom/" + storyId + "/nodes/" + nodeId + ".png";
        String imageS3Url = s3Service.generatePresignedUploadUrl(imageKey).getUrl();
        log.debug("Generated presigned URL for custom image upload: {}", imageKey);

        // Build request with custom prompt
        ImageGenerationRequestDto imageRequest = ImageGenerationRequestDto.builder()
            .storyId(storyId)
            .nodeId(nodeId)
            .nodeText(request.getCustomPrompt())  // Use custom prompt
            .episodeTitle(episode.getTitle())
            .episodeOrder(episode.getOrder())
            .nodeDepth(node.getDepth())
            .imageType("SCENE")  // 커스텀 이미지는 SCENE 타입
            .novelS3Bucket(s3BucketName)
            .novelS3Key("novels/original/" + storyId + ".txt")
            .imageS3Url(imageS3Url)  // AI-IMAGE 서버가 이 URL로 이미지 업로드
            .generateImage(true)  // 이미지 생성 활성화
            .build();

        // Generate via relay server
        ImageGenerationResponseDto response = relayServerClient.generateImage(imageRequest);

        // Extract fileKey if response contains full URL
        String fileKey = extractFileKeyFromUrl(response.getFileKey());

        // Update node - only store fileKey, not direct URL
        node.setImageUrl(null);  // Don't store direct S3 URL
        node.setImageFileKey(fileKey);  // Store extracted fileKey
        storyNodeRepository.save(node);

        log.info("Custom image generated for node {}. FileKey: {}", nodeId, fileKey);

        // Generate presigned download URL for frontend
        String presignedUrl = s3Service.generatePresignedDownloadUrl(fileKey);

        // Return response with presigned URL instead of direct S3 URL
        return ImageGenerationResponseDto.builder()
            .imageUrl(presignedUrl)  // Use presigned URL instead of direct URL
            .fileKey(response.getFileKey())
            .build();
    }

    /**
     * Upload user's custom image for any episode nodes
     */
    @Transactional
    public UploadImageResponseDto uploadCustomImage(
        String storyId,
        String nodeId,
        UploadImageRequestDto request
    ) {
        // Validate node exists
        StoryNode node = validateNode(storyId, nodeId);

        // Generate presigned upload URL
        String fileKey = "images/custom/" + storyId + "/nodes/" + nodeId + ".jpg";
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
    public NodeImageResponseDto getNodeImage(String storyId, String nodeId) {
        StoryNode node = storyNodeRepository.findById(UUID.fromString(nodeId))
            .orElseThrow(() -> new IllegalArgumentException("Node not found: " + nodeId));

        String presignedUrl = null;
        if (node.getImageFileKey() != null) {
            // Extract fileKey if it's a full URL (legacy data)
            String fileKey = extractFileKeyFromUrl(node.getImageFileKey());
            presignedUrl = s3Service.generatePresignedDownloadUrl(fileKey);
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
    private StoryNode validateNode(String storyId, String nodeId) {
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

    /**
     * Extract fileKey from URL if the input is a full S3 URL
     * If input is already a fileKey, return as-is
     */
    private String extractFileKeyFromUrl(String fileKeyOrUrl) {
        if (fileKeyOrUrl == null) {
            return null;
        }

        // If it's a full URL, extract the key part after bucket name
        if (fileKeyOrUrl.startsWith("http://") || fileKeyOrUrl.startsWith("https://")) {
            try {
                // Pattern: https://bucket.s3.region.amazonaws.com/key
                // Extract everything after the first "/" following ".amazonaws.com"
                int amazonIdx = fileKeyOrUrl.indexOf(".amazonaws.com/");
                if (amazonIdx != -1) {
                    String extracted = fileKeyOrUrl.substring(amazonIdx + ".amazonaws.com/".length());

                    // Remove query parameters if present (presigned URL case)
                    int queryIdx = extracted.indexOf("?");
                    if (queryIdx != -1) {
                        extracted = extracted.substring(0, queryIdx);
                    }

                    return extracted;
                }
            } catch (Exception e) {
                log.warn("Failed to extract fileKey from URL: {}", fileKeyOrUrl, e);
            }
        }

        // Already a fileKey, return as-is
        return fileKeyOrUrl;
    }
}
