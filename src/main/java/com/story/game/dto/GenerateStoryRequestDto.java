package com.story.game.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateStoryRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Novel text is required")
    private String novelText;

    @NotNull(message = "Selected gauge IDs are required")
    @Size(min = 2, message = "At least 2 gauges must be selected")
    private List<String> selectedGaugeIds;

    @Min(1)
    @Max(10)
    @Builder.Default
    private Integer numEpisodes = 3;

    @Min(2)
    @Max(5)
    @Builder.Default
    private Integer maxDepth = 3;

    @Builder.Default
    private Map<String, Integer> endingConfig = Map.of(
            "happy", 2,
            "tragic", 1,
            "neutral", 1,
            "open", 1,
            "bad", 0
    );

    @Min(1)
    @Builder.Default
    private Integer numEpisodeEndings = 3;
}
