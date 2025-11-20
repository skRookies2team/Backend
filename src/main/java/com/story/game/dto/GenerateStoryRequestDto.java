package com.story.game.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

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

    @Min(1)
    @Max(10)
    @Builder.Default
    private Integer numEpisodes = 3;

    @Min(1)
    @Max(5)
    @Builder.Default
    private Integer maxDepth = 2;
}
