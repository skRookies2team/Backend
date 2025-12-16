package com.story.game.common.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryChoiceDto {
    private String text;
    private List<String> tags;
    private String immediateReaction;
}
