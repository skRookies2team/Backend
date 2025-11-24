package com.story.game.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryConfigRequestDto {

    private String description;

    @Min(1)
    @Max(10)
    @Builder.Default
    private Integer numEpisodes = 3;

    @Min(2)
    @Max(5)
    @Builder.Default
    private Integer maxDepth = 3;

    @NotNull(message = "Ending configuration is required")
    private Map<String, Integer> endingConfig;

    @Min(1)
    @Max(5)
    @Builder.Default
    private Integer numEpisodeEndings = 3;
}
