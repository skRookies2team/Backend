package com.story.game.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStateResponseDto {
    private String sessionId;
    private String currentEpisodeId;
    private String currentNodeId;
    private Map<String, Integer> gaugeStates;
    private Map<String, Integer> accumulatedTags;

    // Current content
    private String episodeTitle;
    private String introText;
    private String nodeText;
    private StoryNodeDto.StoryNodeDetailDto nodeDetails;
    private List<StoryChoiceDto> choices;

    // Gauge info for display
    private List<GaugeDto> gaugeDefinitions;

    // Progress info
    private Boolean isEpisodeEnd;
    private Boolean isGameEnd;
    private EpisodeEndingDto episodeEnding;
    private FinalEndingDto finalEnding;
}
