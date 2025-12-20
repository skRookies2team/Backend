package com.story.game.creation.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadImageRequestDto {

    @NotBlank(message = "Content type is required")
    private String contentType;  // e.g., "image/jpeg", "image/png"
}
