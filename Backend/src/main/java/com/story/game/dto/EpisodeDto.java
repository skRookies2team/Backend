package com.story.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpisodeDto {
    private String id;
    private String title;
    private Integer order;
    private String description;
    private String theme;

    @JsonProperty("intro_text")
    private String introText;

    private List<StoryNodeDto> nodes;
    private List<EpisodeEndingDto> endings;
}
