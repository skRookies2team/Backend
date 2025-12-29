package com.story.game.gameplay.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.story.game.common.dto.*;
import lombok.*;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStateResponseDto {
    private String sessionId;
    private String characterId;
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

    // Image information for the current node (with type)
    private NodeImageInfo nodeImage;

    // Deprecated: 하위 호환성을 위해 유지, nodeImage.imageUrl 값 반환
    @Deprecated
    @JsonIgnore
    public String getImageUrl() {
        return nodeImage != null ? nodeImage.getImageUrl() : null;
    }

    // Deprecated: 하위 호환성을 위한 setter
    @Deprecated
    @JsonIgnore
    public void setImageUrl(String imageUrl) {
        if (imageUrl != null) {
            this.nodeImage = NodeImageInfo.of(imageUrl, ImageType.SCENE);
        }
    }

    // Gauge info for display
    private List<GaugeDto> gaugeDefinitions;

    // Progress info
    private Boolean isEpisodeEnd;
    private Boolean isGameEnd;
    private EpisodeEndingDto episodeEnding;
    private FinalEndingDto finalEnding;
}
