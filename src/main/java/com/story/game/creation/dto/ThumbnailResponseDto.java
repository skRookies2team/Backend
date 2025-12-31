package com.story.game.creation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for thumbnail retrieval
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThumbnailResponseDto {

    private String thumbnailUrl;  // Presigned download URL (null if not available)
    private boolean hasAiGenerated;  // AI가 자동 생성한 썸네일 여부
}
