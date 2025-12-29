package com.story.game.creation.service;

import com.story.game.ai.dto.ImageGenerationRequestDto;
import com.story.game.ai.dto.ImageGenerationResponseDto;
import com.story.game.ai.dto.NovelStyleLearnRequestDto;
import com.story.game.ai.service.RelayServerClient;
import com.story.game.common.dto.ImageType;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.infrastructure.s3.S3Service;
import com.story.game.story.entity.StoryNode;
import com.story.game.story.repository.StoryNodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationService {

    private final RelayServerClient relayServerClient;
    private final StoryNodeRepository storyNodeRepository;
    private final StoryCreationRepository storyCreationRepository;
    private final S3Service s3Service;
    private final WebClient.Builder webClientBuilder;

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
        String storyId,
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
            // Determine image type
            ImageType imageType = determineImageType(node);
            log.debug("Determined image type for node {}: {}", node.getId(), imageType);

            // Generate S3 presigned URL for image upload
            String imageKey = "story-images/" + storyId + "/" + node.getId() + ".png";
            String imageS3Url = s3Service.generatePresignedUploadUrl(imageKey).getUrl();
            log.debug("Generated presigned URL for image upload: {}", imageKey);

            // Build request
            ImageGenerationRequestDto request = ImageGenerationRequestDto.builder()
                .storyId(storyId)
                .nodeId(node.getId().toString())
                .nodeText(node.getText())
                .situation(node.getSituation())
                .episodeTitle(episodeTitle)
                .episodeOrder(episodeOrder)
                .nodeDepth(node.getDepth())
                .imageType(imageType)  // 이미지 타입 정보 포함
                .novelS3Bucket(s3BucketName)
                .novelS3Key("novels/original/" + storyId + ".txt")
                .imageS3Url(imageS3Url)  // AI-IMAGE 서버가 이 URL로 이미지 업로드
                .build();

            // Call relay server → AI-IMAGE server (synchronous)
            ImageGenerationResponseDto response = null;
            boolean retryWithStyleLearning = false;

            try {
                response = relayServerClient.generateImage(request);
            } catch (Exception e) {
                // Check if error is due to missing novel style
                if (e.getMessage() != null &&
                    (e.getMessage().contains("스타일 정보") ||
                     e.getMessage().contains("style") ||
                     e.getMessage().contains("404"))) {
                    log.warn("Novel style not found for story {}. Will attempt to learn style.", storyId);
                    retryWithStyleLearning = true;
                } else {
                    throw e;
                }
            }

            // If style learning is needed, learn and retry
            if (retryWithStyleLearning) {
                log.info("Attempting to learn novel style for story: {}", storyId);
                boolean styleLearnSuccess = ensureNovelStyleLearned(storyId);

                if (styleLearnSuccess) {
                    log.info("Novel style learned successfully. Retrying image generation...");
                    response = relayServerClient.generateImage(request);
                } else {
                    log.error("Failed to learn novel style for story: {}", storyId);
                    return;
                }
            }

            if (response == null || response.getImageUrl() == null || response.getImageUrl().isEmpty()) {
                log.warn("No image URL returned from AI server for node {}", node.getId());
                return;
            }

            log.info("Image generated by AI server: {}", response.getImageUrl());

            // Download image from AI-IMAGE server
            byte[] imageBytes = downloadImage(response.getImageUrl());

            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Failed to download image for node {}", node.getId());
                return;
            }

            log.info("Image downloaded successfully: {} bytes", imageBytes.length);

            // Upload to S3
            String s3FileKey = "images/" + storyId + "/" + node.getId() + ".png";
            String s3Url = s3Service.uploadBinaryFile(s3FileKey, imageBytes, "image/png");

            log.info("Image uploaded to S3: {}", s3FileKey);

            // Update node with S3 info
            node.setImageUrl(s3Url);
            node.setImageFileKey(s3FileKey);
            storyNodeRepository.save(node);

            log.info("✅ Image generated and saved for node {}: S3 key={}",
                node.getId(), s3FileKey);

        } catch (Exception e) {
            log.error("Failed to generate image for node {}: {}",
                node.getId(), e.getMessage(), e);
            // Don't fail the entire story generation if image fails
        }
    }

    /**
     * Ensure novel style is learned for a story
     * Downloads novel from S3 and calls style learning API
     *
     * @param storyId Story ID
     * @return true if style learning succeeded, false otherwise
     */
    private boolean ensureNovelStyleLearned(String storyId) {
        try {
            log.info("Ensuring novel style is learned for story: {}", storyId);

            // Get story creation to access title and novel
            StoryCreation storyCreation = storyCreationRepository.findById(storyId)
                .orElse(null);

            if (storyCreation == null) {
                log.error("Story creation not found: {}", storyId);
                return false;
            }

            // Get novel text - either from DB or S3
            String novelText = storyCreation.getNovelText();

            if (novelText == null || novelText.isEmpty()) {
                // Try to download from S3
                String novelFileKey = "novels/original/" + storyId + ".txt";
                try {
                    novelText = s3Service.downloadFileContent(novelFileKey);
                    log.info("Downloaded novel from S3: {} ({} characters)",
                        novelFileKey, novelText.length());
                } catch (Exception e) {
                    log.error("Failed to download novel from S3: {}", novelFileKey, e);
                    return false;
                }
            }

            if (novelText == null || novelText.isEmpty()) {
                log.error("No novel text available for story: {}", storyId);
                return false;
            }

            // Build style learning request
            NovelStyleLearnRequestDto styleRequest = NovelStyleLearnRequestDto.builder()
                .story_id(storyId)
                .novel_text(novelText)
                .title(storyCreation.getTitle())
                .build();

            // Call relay server to learn style
            com.story.game.ai.dto.NovelStyleLearnResponseDto result = relayServerClient.learnNovelStyle(styleRequest);

            if (result != null) {
                log.info("✅ Novel style learned successfully for story: {}", storyId);
                return true;
            } else {
                log.warn("Novel style learning returned null for story: {}", storyId);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to ensure novel style learned for story {}: {}",
                storyId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Download image from AI-IMAGE server URL
     */
    private byte[] downloadImage(String imageUrl) {
        try {
            log.info("Downloading image from: {}", imageUrl);

            WebClient webClient = webClientBuilder.build();

            byte[] imageBytes = webClient.get()
                .uri(imageUrl)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();

            if (imageBytes != null) {
                log.info("Image downloaded: {} bytes", imageBytes.length);
            }

            return imageBytes;

        } catch (Exception e) {
            log.error("Failed to download image from {}: {}", imageUrl, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Determine image type based on node properties
     */
    private ImageType determineImageType(StoryNode node) {
        // Root nodes (depth=0) → EPISODE_START
        if (node.getDepth() != null && node.getDepth() == 0) {
            return ImageType.EPISODE_START;
        }

        // Ending nodes (no outgoing choices) → EPISODE_ENDING
        if (node.getOutgoingChoices() == null || node.getOutgoingChoices().isEmpty()) {
            return ImageType.EPISODE_ENDING;
        }

        // Other nodes → SCENE
        return ImageType.SCENE;
    }
}
