package com.story.game.dto;

import com.story.game.entity.StoryCreation;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryGaugesResponseDto {

    private String storyId;
    private StoryCreation.CreationStatus status;
    private List<GaugeDto> gauges;
}
