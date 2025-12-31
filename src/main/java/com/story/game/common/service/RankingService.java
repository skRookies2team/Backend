package com.story.game.common.service;

import com.story.game.achievement.entity.UserAchievement;
import com.story.game.achievement.repository.UserAchievementRepository;
import com.story.game.auth.entity.User;
import com.story.game.auth.repository.UserRepository;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final UserRepository userRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final StoryDataRepository storyDataRepository;
    private final StoryCreationRepository storyCreationRepository;

    /**
     * 유저 랭킹 (업적 포인트 기반)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getUserRankingByAchievements(int limit) {
        List<User> allUsers = userRepository.findAll();

        return allUsers.stream()
                .map(user -> {
                    List<UserAchievement> achievements = userAchievementRepository.findByUserAndIsUnlocked(user, true);
                    int totalPoints = achievements.stream()
                            .mapToInt(ua -> ua.getAchievement().getPoints())
                            .sum();

                    Map<String, Object> userRanking = new HashMap<>();
                    userRanking.put("userId", user.getId());
                    userRanking.put("username", user.getUsername());
                    userRanking.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                    userRanking.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
                    userRanking.put("totalPoints", totalPoints);
                    userRanking.put("achievementCount", achievements.size());
                    return userRanking;
                })
                .sorted((a, b) -> Integer.compare((Integer) b.get("totalPoints"), (Integer) a.get("totalPoints")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 인기 작가 랭킹 (작성한 스토리의 총 좋아요 + 조회수 기반)
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getPopularCreatorsRanking(int limit) {
        List<User> allUsers = userRepository.findAll();

        return allUsers.stream()
                .map(user -> {
                    List<StoryCreation> storyCreations = storyCreationRepository.findByUserOrderByCreatedAtDesc(user);

                    long totalViews = 0L;
                    long totalLikes = 0L;
                    int storyCount = 0;

                    for (StoryCreation storyCreation : storyCreations) {
                        if (storyCreation.getStoryDataId() != null) {
                            Optional<StoryData> storyDataOpt = storyDataRepository.findById(storyCreation.getStoryDataId());
                            if (storyDataOpt.isPresent()) {
                                StoryData storyData = storyDataOpt.get();
                                totalViews += storyData.getViewCount() != null ? storyData.getViewCount() : 0L;
                                totalLikes += storyData.getLikesCount() != null ? storyData.getLikesCount() : 0L;
                                storyCount++;
                            }
                        }
                    }

                    long popularityScore = totalViews + (totalLikes * 2); // 좋아요에 가중치

                    Map<String, Object> creatorRanking = new HashMap<>();
                    creatorRanking.put("userId", user.getId());
                    creatorRanking.put("username", user.getUsername());
                    creatorRanking.put("nickname", user.getNickname() != null ? user.getNickname() : user.getUsername());
                    creatorRanking.put("profileImageUrl", user.getProfileImageUrl() != null ? user.getProfileImageUrl() : "");
                    creatorRanking.put("storyCount", storyCount);
                    creatorRanking.put("totalViews", totalViews);
                    creatorRanking.put("totalLikes", totalLikes);
                    creatorRanking.put("popularityScore", popularityScore);
                    return creatorRanking;
                })
                .filter(map -> (int) map.get("storyCount") > 0) // 스토리가 있는 작가만
                .sorted((a, b) -> Long.compare((Long) b.get("popularityScore"), (Long) a.get("popularityScore")))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 이번 주 인기 스토리 (최근 7일 기준)
     */
    @Transactional(readOnly = true)
    public List<StoryData> getWeeklyPopularStories(int limit) {
        // 간단하게 조회수 기준 (실제로는 날짜 필터링 필요)
        return storyDataRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(
                        b.getViewCount() != null ? b.getViewCount() : 0L,
                        a.getViewCount() != null ? a.getViewCount() : 0L
                ))
                .limit(limit)
                .collect(Collectors.toList());
    }
}
