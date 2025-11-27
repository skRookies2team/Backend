package com.story.game.creation.dto;

import com.story.game.creation.entity.StoryCreation;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryUploadResponseDto {

    private String storyId;
    private String title;
    private StoryCreation.CreationStatus status;
    private LocalDateTime createdAt;
}
