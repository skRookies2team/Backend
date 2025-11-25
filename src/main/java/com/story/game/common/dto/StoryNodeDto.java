package com.story.game.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryNodeDto {
    private String id;
    private Integer depth;
    private String text;
    private StoryNodeDetailDto details;
    private List<StoryChoiceDto> choices;

    @JsonProperty("parent_id")
    private String parentId;

    @JsonProperty("node_type")
    private String nodeType;

    @JsonProperty("episode_id")
    private String episodeId;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StoryNodeDetailDto {
        @JsonProperty("npc_emotions")
        private Map<String, String> npcEmotions;
        private String situation;
        @JsonProperty("relations_update")
        private Map<String, String> relationsUpdate;
    }
}
