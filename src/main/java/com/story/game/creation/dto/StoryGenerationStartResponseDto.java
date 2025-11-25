package com.story.game.creation.dto;

import com.story.game.creation.entity.StoryCreation;
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
