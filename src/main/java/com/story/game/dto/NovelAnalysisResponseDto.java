package com.story.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NovelAnalysisResponseDto {
    private String summary;
    private List<CharacterDto> characters;
    private List<GaugeDto> gauges;
}