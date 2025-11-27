package com.story.game.creation.dto;

import com.story.game.creation.entity.StoryCreation;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StorySummaryResponseDto {

    private String storyId;
    private StoryCreation.CreationStatus status;
    private String summary;
}
