package com.story.game.creation.repository;

import com.story.game.auth.entity.User;
import com.story.game.creation.entity.StoryCreation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoryCreationRepository extends JpaRepository<StoryCreation, String> {
    Optional<StoryCreation> findByStoryDataId(Long storyDataId);
    long countByUserAndStatus(User user, StoryCreation.CreationStatus status);
}
