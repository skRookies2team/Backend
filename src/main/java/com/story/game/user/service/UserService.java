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
import com.story.game.infrastructure.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GameSessionRepository gameSessionRepository;
    private final StoryDataRepository storyDataRepository;
    private final AchievementService achievementService;
    private final PasswordEncoder passwordEncoder;
    private final S3Service s3Service;

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

    @Transactional
    public ProfileImageUploadResponseDto uploadProfileImage(String username, MultipartFile imageFile) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 파일 검증
        if (imageFile.isEmpty()) {
            throw new IllegalArgumentException("Image file is required");
        }

        // 파일 타입 검증 (이미지만 허용)
        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }

        // 파일 크기 검증 (5MB 제한)
        if (imageFile.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Image file size must be less than 5MB");
        }

        try {
            // 원본 파일명에서 확장자 추출
            String originalFilename = imageFile.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 고유한 파일명 생성 (profile-images/{userId}/{uuid}.{extension})
            String fileName = "profile-images/" + user.getId() + "/" + UUID.randomUUID() + extension;

            // S3에 업로드
            byte[] fileBytes = imageFile.getBytes();
            String fileKey = s3Service.uploadBinaryFile(fileName, fileBytes, contentType);

            // S3 다운로드 URL 생성
            String profileImageUrl = s3Service.generatePresignedDownloadUrl(fileKey);

            // 사용자 프로필 이미지 URL 업데이트
            user.updateProfile(null, null, profileImageUrl);
            userRepository.save(user);

            return ProfileImageUploadResponseDto.builder()
                    .profileImageUrl(profileImageUrl)
                    .message("Profile image uploaded successfully")
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to upload profile image: " + e.getMessage());
        }
    }
}
