package com.story.game.common.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryChoiceDto {
    private String id;
    private String text;
    private String targetNodeId;
    private Map<String, Integer> gaugeEffects;
    private List<String> tags;
}
