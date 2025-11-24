package com.story.game.dto;

import com.story.game.entity.StoryCreation;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryGenerationStartResponseDto {

    private String storyId;
    private StoryCreation.CreationStatus status;
    private String message;
    private String estimatedTime;
}
