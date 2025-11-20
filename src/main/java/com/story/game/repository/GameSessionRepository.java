package com.story.game.repository;

import com.story.game.entity.GameSession;
import com.story.game.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, String> {

    List<GameSession> findByStoryDataIdAndIsCompletedFalse(Long storyDataId);

    List<GameSession> findByIsCompletedFalseOrderByUpdatedAtDesc();

    List<GameSession> findByUser(User user);

    List<GameSession> findByUserOrderByCreatedAtDesc(User user);

    long countByUser(User user);

    long countByUserAndIsCompleted(User user, Boolean isCompleted);

    @Query("SELECT COUNT(DISTINCT g.finalEndingId) FROM GameSession g WHERE g.user = :user AND g.finalEndingId IS NOT NULL")
    long countDistinctFinalEndingsByUser(User user);
}
