package com.story.game.achievement.service;

import com.story.game.achievement.entity.Achievement;
import com.story.game.auth.entity.User;
import com.story.game.achievement.entity.UserAchievement;
import com.story.game.achievement.repository.AchievementRepository;
import com.story.game.community.repository.PostRepository;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.gameplay.repository.GameSessionRepository;
import com.story.game.achievement.repository.UserAchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final GameSessionRepository gameSessionRepository;
    private final StoryCreationRepository storyCreationRepository;
    private final PostRepository postRepository;

    // 사용자의 업적 진행 상황 체크 및 업데이트
    @Transactional
    public void checkAndUpdateAchievements(User user) {
        List<Achievement> allAchievements = achievementRepository.findAll();

        for (Achievement achievement : allAchievements) {
            UserAchievement userAchievement = userAchievementRepository
                    .findByUserAndAchievement(user, achievement)
                    .orElseGet(() -> createUserAchievement(user, achievement));

            if (!userAchievement.getIsUnlocked()) {
                Integer currentValue = calculateCurrentValue(user, achievement);
                userAchievement.updateProgress(currentValue);
                userAchievementRepository.save(userAchievement);
            }
        }
    }

    // 특정 업적 진행도 업데이트
    @Transactional
    public void updateAchievementProgress(User user, String achievementCode, Integer value) {
        Achievement achievement = achievementRepository.findByCode(achievementCode)
                .orElseThrow(() -> new IllegalArgumentException("Achievement not found"));

        UserAchievement userAchievement = userAchievementRepository
                .findByUserAndAchievement(user, achievement)
                .orElseGet(() -> createUserAchievement(user, achievement));

        userAchievement.updateProgress(value);
        userAchievementRepository.save(userAchievement);
    }

    // 사용자의 모든 업적 조회
    @Transactional(readOnly = true)
    public List<UserAchievement> getUserAchievements(User user) {
        return userAchievementRepository.findByUser(user);
    }

    // 사용자의 달성한 업적 조회
    @Transactional(readOnly = true)
    public List<UserAchievement> getUnlockedAchievements(User user) {
        return userAchievementRepository.findByUserAndIsUnlocked(user, true);
    }

    // 업적 달성률 계산
    @Transactional(readOnly = true)
    public Double getAchievementRate(User user) {
        long totalAchievements = achievementRepository.count();
        long unlockedAchievements = userAchievementRepository.countByUserAndIsUnlocked(user, true);

        if (totalAchievements == 0) {
            return 0.0;
        }

        return (double) unlockedAchievements / totalAchievements * 100;
    }

    // UserAchievement 생성
    private UserAchievement createUserAchievement(User user, Achievement achievement) {
        return UserAchievement.builder()
                .user(user)
                .achievement(achievement)
                .currentValue(0)
                .isUnlocked(false)
                .build();
    }

    // 업적 타입별 현재 값 계산
    private Integer calculateCurrentValue(User user, Achievement achievement) {
        return switch (achievement.getType()) {
            case PLAY_COUNT -> (int) gameSessionRepository.countByUser(user);
            case COMPLETION_COUNT -> (int) gameSessionRepository.countByUserAndIsCompleted(user, true);
            case ENDING_COUNT -> (int) gameSessionRepository.countDistinctFinalEndingsByUser(user);
            case CREATION_COUNT -> (int) storyCreationRepository.countByUserAndStatus(user, StoryCreation.CreationStatus.COMPLETED);
            case POST_COUNT -> (int) postRepository.countByAuthor(user);
        };
    }
}
