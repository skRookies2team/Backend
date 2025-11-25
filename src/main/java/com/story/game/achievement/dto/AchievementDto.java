package com.story.game.achievement.dto;

import com.story.game.achievement.entity.Achievement;
import com.story.game.achievement.entity.UserAchievement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AchievementDto {
    private Long achievementId;
    private String code;
    private String name;
    private String description;
    private String type;
    private Integer targetValue;
    private Integer currentValue;
    private String iconUrl;
    private Integer points;
    private Boolean isUnlocked;
    private LocalDateTime unlockedAt;

    public static AchievementDto from(UserAchievement userAchievement) {
        Achievement achievement = userAchievement.getAchievement();
        return AchievementDto.builder()
                .achievementId(achievement.getId())
                .code(achievement.getCode())
                .name(achievement.getName())
                .description(achievement.getDescription())
                .type(achievement.getType().name())
                .targetValue(achievement.getTargetValue())
                .currentValue(userAchievement.getCurrentValue())
                .iconUrl(achievement.getIconUrl())
                .points(achievement.getPoints())
                .isUnlocked(userAchievement.getIsUnlocked())
                .unlockedAt(userAchievement.getUnlockedAt())
                .build();
    }
}
