package com.story.game.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GaugeDto {
    private String id;
    private String name;
    private String meaning;

    @JsonProperty("min_label")
    private String minLabel;

    @JsonProperty("max_label")
    private String maxLabel;

    private String description;
}
