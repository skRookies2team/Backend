package com.story.game.creation.dto;

import com.story.game.common.dto.*;
import com.story.game.creation.entity.StoryCreation;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryResultResponseDto {

    private String storyId;
    private StoryCreation.CreationStatus status;
    private Long storyDataId;
    private MetadataData metadata;
    private PreviewData preview;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataData {
        private String title;
        private String genre;
        private String description;
        private Integer totalEpisodes;
        private Integer totalNodes;
        private Integer totalGauges;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PreviewData {
        private String firstEpisodeTitle;
        private String firstEpisodeIntro;
        private List<GaugeDto> selectedGauges;
    }
}
