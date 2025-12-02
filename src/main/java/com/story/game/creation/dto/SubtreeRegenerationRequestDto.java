package com.story.game.creation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtreeRegenerationRequestDto {

    private String episodeTitle;
    private int episodeOrder;
    private ParentNodeDto parentNode;
    private int currentDepth;
    private int maxDepth;
    private String novelContext;
    private List<String> previousChoices;
    private List<String> selectedGaugeIds;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParentNodeDto {
        private String nodeId;
        private String text;
        private List<String> choices;
        private String situation;
        private Map<String, String> npcEmotions;
        private List<String> tags;
        private int depth;
    }
}
