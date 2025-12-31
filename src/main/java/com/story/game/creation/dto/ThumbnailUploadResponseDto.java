package com.story.game.creation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for thumbnail upload
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThumbnailUploadResponseDto {

    private String thumbnailUrl;  // Presigned download URL for the uploaded thumbnail
    private String message;
}
