package com.story.game.dto;

import com.story.game.entity.StoryCreation;
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
