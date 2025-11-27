package com.story.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryGenerationRequestDto {

    @NotBlank(message = "Novel text is required")
    @JsonProperty("novel_text")
    private String novelText;

    @Min(1)
    @Max(10)
    @JsonProperty("num_episodes")
    @Builder.Default
    private Integer numEpisodes = 3;

    @Min(1)
    @Max(5)
    @JsonProperty("max_depth")
    @Builder.Default
    private Integer maxDepth = 2;
}
