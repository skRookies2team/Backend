package com.story.game.creation.dto;

import com.story.game.common.dto.EpisodeDto;
import com.story.game.creation.entity.StoryCreation;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryProgressResponseDto {

    private String storyId;
    private StoryCreation.CreationStatus status;
    private ProgressData progress;
    private List<EpisodeDto> episodes;  // 생성된 에피소드 목록

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProgressData {
        private String currentPhase;
        private Integer completedEpisodes;
        private Integer totalEpisodes;
        private Integer percentage;
        private String message;
        private String error;
    }
}
