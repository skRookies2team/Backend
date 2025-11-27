package com.story.game.dto;

import com.story.game.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileDto {
    private Long userId;
    private String username;
    private String email;
    private String nickname;
    private String bio;
    private String profileImageUrl;
    private LocalDateTime createdAt;
    private Long totalPlayCount;
    private Long completedStoryCount;
    private Long unlockedEndingCount;
    private Long unlockedAchievementCount;
    private Double achievementRate;

    public static UserProfileDto from(User user, Long totalPlayCount, Long completedStoryCount,
                                      Long unlockedEndingCount, Long unlockedAchievementCount,
                                      Double achievementRate) {
        return UserProfileDto.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .bio(user.getBio())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .totalPlayCount(totalPlayCount)
                .completedStoryCount(completedStoryCount)
                .unlockedEndingCount(unlockedEndingCount)
                .unlockedAchievementCount(unlockedAchievementCount)
                .achievementRate(achievementRate)
                .build();
    }
}
