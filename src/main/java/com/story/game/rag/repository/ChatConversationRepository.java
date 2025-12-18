package com.story.game.rag.repository;

import com.story.game.auth.entity.User;
import com.story.game.rag.entity.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    Optional<ChatConversation> findByUserAndCharacterId(User user, String characterId);

    List<ChatConversation> findByUserOrderByUpdatedAtDesc(User user);

    List<ChatConversation> findByUserAndStoryIdOrderByUpdatedAtDesc(User user, String storyId);
}
