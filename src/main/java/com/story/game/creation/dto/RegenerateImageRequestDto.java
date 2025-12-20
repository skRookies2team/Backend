package com.story.game.creation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegenerateImageRequestDto {

    @NotBlank(message = "Custom prompt is required")
    @Size(max = 500, message = "Custom prompt must not exceed 500 characters")
    private String customPrompt;
}
