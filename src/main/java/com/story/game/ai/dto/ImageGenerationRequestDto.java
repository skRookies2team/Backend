package com.story.game.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationRequestDto {
    private String storyId;
    private String nodeId;
    private String nodeText;
    private String situation;
    private Map<String, String> npcEmotions;
    private String episodeTitle;
    private Integer episodeOrder;
    private Integer nodeDepth;
    private String imageStyle;
    private String additionalContext;
    private String novelS3Bucket;
    private String novelS3Key;
    private String imageS3Url;  // 이미지 업로드용 S3 presigned URL (AI-IMAGE 서버에서 사용)
}
