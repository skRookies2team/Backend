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

    /**
     * AI가 소설 상황에 맞춰 설정한 게이지 초기값 (0~100)
     * 예: 평화로운 시작이면 hope=70, 위기 상황이면 hope=30
     */
    @JsonProperty("initial_value")
    private Integer initialValue;
}
