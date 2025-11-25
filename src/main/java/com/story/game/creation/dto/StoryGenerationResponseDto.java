package com.story.game.creation.dto;

import com.story.game.common.dto.FullStoryDto;
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
