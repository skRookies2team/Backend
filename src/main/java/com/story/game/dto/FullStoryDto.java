package com.story.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FullStoryDto {

    private MetadataDto metadata;
    private ContextDto context;
    private List<EpisodeDto> episodes;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataDto {
        @JsonProperty("total_episodes")
        private Integer totalEpisodes;

        @JsonProperty("total_nodes")
        private Integer totalNodes;

        private List<String> gauges;

        @JsonProperty("character_count")
        private Integer characterCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextDto {
        @JsonProperty("novel_summary")
        private String novelSummary;

        private List<CharacterDto> characters;

        @JsonProperty("selected_gauges")
        private List<GaugeDto> selectedGauges;

        @JsonProperty("final_endings")
        private List<FinalEndingDto> finalEndings;
    }
}
