package com.story.game.user.service;

import com.story.game.achievement.service.AchievementService;
import com.story.game.user.dto.*;
import com.story.game.auth.dto.*;
import com.story.game.achievement.dto.*;
import com.story.game.gameplay.entity.GameSession;
import com.story.game.common.entity.StoryData;
import com.story.game.auth.entity.User;
import com.story.game.achievement.entity.UserAchievement;
import com.story.game.gameplay.repository.GameSessionRepository;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GameSessionRepository gameSessionRepository;
    private final StoryDataRepository storyDataRepository;
    private final AchievementService achievementService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UserProfileDto getUserProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Long totalPlayCount = gameSessionRepository.countByUser(user);
        Long completedStoryCount = gameSessionRepository.countByUserAndIsCompleted(user, true);
        Long unlockedEndingCount = gameSessionRepository.countDistinctFinalEndingsByUser(user);
        Long unlockedAchievementCount = (long) achievementService.getUnlockedAchievements(user).size();
        Double achievementRate = achievementService.getAchievementRate(user);

        return UserProfileDto.from(user, totalPlayCount, completedStoryCount,
                unlockedEndingCount, unlockedAchievementCount, achievementRate);
    }

    @Transactional
    public UserProfileDto updateProfile(String username, UpdateProfileRequestDto request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        user.updateProfile(request.getNickname(), request.getBio(), request.getProfileImageUrl());
        userRepository.save(user);

        return getUserProfile(username);
    }

    @Transactional(readOnly = true)
    public List<GameHistoryDto> getGameHistory(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<GameSession> sessions = gameSessionRepository.findByUserOrderByCreatedAtDesc(user);

        return sessions.stream()
                .map(session -> {
                    StoryData storyData = storyDataRepository.findById(session.getStoryDataId())
                            .orElse(null);
                    String storyTitle = storyData != null ? storyData.getTitle() : "Unknown";
                    return GameHistoryDto.from(session, storyTitle);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AchievementDto> getUserAchievements(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 업적 체크 및 업데이트
        achievementService.checkAndUpdateAchievements(user);

        List<UserAchievement> userAchievements = achievementService.getUserAchievements(user);

        return userAchievements.stream()
                .map(AchievementDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updatePassword(String username, String currentPassword, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}
