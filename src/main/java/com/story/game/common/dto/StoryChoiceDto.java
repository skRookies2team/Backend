package com.story.game.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryChoiceDto {
    private String text;
    private List<String> tags;

    @JsonProperty("immediate_reaction")
    private String immediateReaction;
}
