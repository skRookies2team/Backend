package com.story.game.user.service;

import com.story.game.achievement.service.AchievementService;
import com.story.game.user.dto.*;
import com.story.game.auth.dto.*;
import com.story.game.achievement.dto.*;
import com.story.game.gameplay.entity.GameSession;
import com.story.game.common.entity.StoryData;
import com.story.game.auth.entity.User;
import com.story.game.achievement.entity.UserAchievement;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.gameplay.repository.GameSessionRepository;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.auth.repository.UserRepository;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.common.exception.ExternalServiceException;
import com.story.game.infrastructure.config.FileUploadProperties;
import com.story.game.infrastructure.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final GameSessionRepository gameSessionRepository;
    private final StoryDataRepository storyDataRepository;
    private final StoryCreationRepository storyCreationRepository;
    private final AchievementService achievementService;
    private final PasswordEncoder passwordEncoder;
    private final S3Service s3Service;
    private final FileUploadProperties uploadProperties;

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

                    // Generate thumbnail URL if available
                    String thumbnailUrl = null;
                    if (storyData != null && storyData.getThumbnailFileKey() != null && !storyData.getThumbnailFileKey().isBlank()) {
                        try {
                            thumbnailUrl = s3Service.generatePresignedDownloadUrl(storyData.getThumbnailFileKey());
                        } catch (Exception e) {
                            log.warn("Failed to generate thumbnail URL for story {}: {}", storyData.getId(), e.getMessage());
                        }
                    }

                    return GameHistoryDto.from(session, storyTitle, thumbnailUrl);
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

        // 파일 크기 검증 (설정값 사용)
        long maxSize = uploadProperties.getMaxSize().getImage();
        if (imageFile.getSize() > maxSize) {
            long maxSizeMB = maxSize / (1024 * 1024);
            throw new IllegalArgumentException("Image file size must be less than " + maxSizeMB + "MB");
        }

        try {
            // 기존 프로필 이미지가 있다면 S3에서 삭제
            String oldProfileImageUrl = user.getProfileImageUrl();
            if (oldProfileImageUrl != null && !oldProfileImageUrl.isEmpty()) {
                try {
                    // URL에서 fileKey 추출 (profile-images/ 경로 패턴 매칭)
                    String oldFileKey = extractFileKeyFromUrl(oldProfileImageUrl);
                    if (oldFileKey != null) {
                        s3Service.deleteFile(oldFileKey);
                        log.info("Deleted old profile image: {}", oldFileKey);
                    }
                } catch (Exception e) {
                    // 기존 이미지 삭제 실패는 로그만 남기고 진행 (신규 업로드를 막지 않음)
                    log.warn("Failed to delete old profile image: {}", e.getMessage());
                }
            }

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
            throw new ExternalServiceException("Failed to upload profile image: " + e.getMessage(), e);
        }
    }

    /**
     * URL에서 S3 fileKey 추출
     * Pre-signed URL 또는 일반 S3 URL에서 profile-images/로 시작하는 파일 경로를 추출
     */
    private String extractFileKeyFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // profile-images/ 패턴 찾기
        int profileImagesIndex = url.indexOf("profile-images/");
        if (profileImagesIndex == -1) {
            return null;
        }

        // profile-images/부터 끝까지 또는 쿼리 파라미터 전까지 추출
        String fileKey = url.substring(profileImagesIndex);

        // 쿼리 파라미터가 있다면 제거 (Pre-signed URL의 경우)
        int queryIndex = fileKey.indexOf("?");
        if (queryIndex != -1) {
            fileKey = fileKey.substring(0, queryIndex);
        }

        return fileKey;
    }

    /**
     * 사용자가 작성한 스토리 목록 조회
     */
    @Transactional(readOnly = true)
    public List<CreatedStoryDto> getCreatedStories(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<StoryCreation> storyCreations = storyCreationRepository.findByUserOrderByCreatedAtDesc(user);

        return storyCreations.stream()
                .map(storyCreation -> {
                    // Generate thumbnail URL if available
                    String thumbnailUrl = null;
                    if (storyCreation.getThumbnailFileKey() != null && !storyCreation.getThumbnailFileKey().isBlank()) {
                        try {
                            thumbnailUrl = s3Service.generatePresignedDownloadUrl(storyCreation.getThumbnailFileKey());
                        } catch (Exception e) {
                            log.warn("Failed to generate thumbnail URL for story {}: {}", storyCreation.getId(), e.getMessage());
                        }
                    }

                    // Get likes count and view count from StoryData if story is completed
                    Long likesCount = 0L;
                    Long viewCount = 0L;
                    if (storyCreation.getStoryDataId() != null) {
                        StoryData storyData = storyDataRepository.findById(storyCreation.getStoryDataId()).orElse(null);
                        if (storyData != null) {
                            likesCount = storyData.getLikesCount() != null ? storyData.getLikesCount() : 0L;
                            viewCount = storyData.getViewCount() != null ? storyData.getViewCount() : 0L;
                        }
                    }

                    return CreatedStoryDto.from(storyCreation, thumbnailUrl, likesCount, viewCount);
                })
                .collect(Collectors.toList());
    }
}
