package com.story.game.repository;

import com.story.game.entity.Achievement;
import com.story.game.entity.User;
import com.story.game.entity.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {
    List<UserAchievement> findByUser(User user);
    List<UserAchievement> findByUserAndIsUnlocked(User user, Boolean isUnlocked);
    Optional<UserAchievement> findByUserAndAchievement(User user, Achievement achievement);
    long countByUserAndIsUnlocked(User user, Boolean isUnlocked);
}
