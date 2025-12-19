package com.story.game.creation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadImageResponseDto {
    private String uploadUrl;      // Presigned URL for upload
    private String fileKey;         // S3 file key
    private Integer expiresIn;      // Seconds until URL expires
}
