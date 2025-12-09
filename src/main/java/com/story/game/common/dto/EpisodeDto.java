package com.story.game.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonProperty(value = "intro_text", access = JsonProperty.Access.READ_WRITE)
    @JsonAlias("introText")  // camelCase도 받을 수 있도록
    private String introText;

    private List<StoryNodeDto> nodes;
    private List<EpisodeEndingDto> endings;
}
