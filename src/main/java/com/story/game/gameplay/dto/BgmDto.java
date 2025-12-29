package com.story.game.gameplay.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * BGM recommendation data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BgmDto {

    private String mood;
    private String filename;
    private String streamingUrl;
    private Double intensity;
    private List<String> emotionalTags;
}
