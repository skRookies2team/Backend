package com.story.game.repository;

import com.story.game.entity.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GameSessionRepository extends JpaRepository<GameSession, String> {

    List<GameSession> findByStoryDataIdAndIsCompletedFalse(Long storyDataId);

    List<GameSession> findByIsCompletedFalseOrderByUpdatedAtDesc();
}
