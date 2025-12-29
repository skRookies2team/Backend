package com.story.game.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpisodeEndingDto {
    private String id;
    private String title;
    private String condition;
    private String text;

    @JsonProperty("gauge_changes")
    private Map<String, Integer> gaugeChanges;

    // Episode ending image (엔딩 이미지)
    private String imageUrl;
    private String imageFileKey;
}
