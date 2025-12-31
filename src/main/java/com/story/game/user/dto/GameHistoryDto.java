package com.story.game.user.dto;

import com.story.game.gameplay.entity.GameSession;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameHistoryDto {
    private String sessionId;
    private Long storyDataId;
    private String storyTitle;
    private String thumbnailUrl;
    private Boolean isCompleted;
    private String finalEndingId;
    private Map<String, Integer> gaugeStates;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static GameHistoryDto from(GameSession session, String storyTitle, String thumbnailUrl) {
        return GameHistoryDto.builder()
                .sessionId(session.getId())
                .storyDataId(session.getStoryDataId())
                .storyTitle(storyTitle)
                .thumbnailUrl(thumbnailUrl)
                .isCompleted(session.getIsCompleted())
                .finalEndingId(session.getFinalEndingId())
                .gaugeStates(session.getGaugeStates())
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
