package com.story.game.achievement.repository;

import com.story.game.achievement.entity.Achievement;
import com.story.game.auth.entity.User;
import com.story.game.achievement.entity.UserAchievement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {
    // N+1 최적화: 업적 정보를 함께 로드
    @EntityGraph(attributePaths = {"achievement"})
    List<UserAchievement> findByUser(User user);

    @EntityGraph(attributePaths = {"achievement"})
    List<UserAchievement> findByUserAndIsUnlocked(User user, Boolean isUnlocked);

    @EntityGraph(attributePaths = {"achievement"})
    Optional<UserAchievement> findByUserAndAchievement(User user, Achievement achievement);

    long countByUserAndIsUnlocked(User user, Boolean isUnlocked);
}
