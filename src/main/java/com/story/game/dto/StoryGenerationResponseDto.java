package com.story.game.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryGenerationResponseDto {
    private String status;
    private String message;
    private FullStoryDto data;
}
