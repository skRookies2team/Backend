package com.story.game.creation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryGenerationRequestDto {

    @JsonProperty("novel_text")
    private String novelText;

    @JsonProperty("file_key")
    private String fileKey;

    @JsonProperty("bucket")
    @Builder.Default
    private String bucket = "story-game-bucket";

    @NotNull(message = "Selected gauge IDs are required")
    @Size(min = 2, message = "At least 2 gauges must be selected")
    @JsonProperty("selected_gauge_ids")
    private List<String> selectedGaugeIds;

    @Min(1)
    @Max(10)
    @JsonProperty("num_episodes")
    @Builder.Default
    private Integer numEpisodes = 3;

    @Min(2)
    @Max(5)
    @JsonProperty("max_depth")
    @Builder.Default
    private Integer maxDepth = 3;

    @JsonProperty("ending_config")
    @Builder.Default
    private Map<String, Integer> endingConfig = Map.of(
            "happy", 2,
            "tragic", 1,
            "neutral", 1,
            "open", 1,
            "bad", 0
    );

    @Min(1)
    @JsonProperty("num_episode_endings")
    @Builder.Default
    private Integer numEpisodeEndings = 3;
}
