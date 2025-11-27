package com.story.game.creation.dto;

import com.story.game.creation.entity.StoryCreation;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryConfigResponseDto {

    private String storyId;
    private StoryCreation.CreationStatus status;
    private ConfigData config;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConfigData {
        private String description;
        private Integer numEpisodes;
        private Integer maxDepth;
        private Map<String, Integer> endingConfig;
        private Integer numEpisodeEndings;
    }
}
