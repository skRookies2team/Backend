package com.story.game.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationResponseDto {
    private String imageUrl;
    private String fileKey;
    private String generatedAt;
}
