package com.story.game.creation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class S3UploadRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "File key is required")
    private String fileKey;
}
