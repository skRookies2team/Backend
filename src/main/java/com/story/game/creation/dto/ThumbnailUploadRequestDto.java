package com.story.game.creation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for uploading custom thumbnail
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ThumbnailUploadRequestDto {

    @NotBlank(message = "Thumbnail file key is required")
    private String thumbnailFileKey;  // S3에 업로드된 썸네일 파일 키
}
