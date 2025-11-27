package com.story.game.creation.repository;

import com.story.game.creation.entity.StoryCreation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryCreationRepository extends JpaRepository<StoryCreation, String> {

    List<StoryCreation> findByStatusOrderByCreatedAtDesc(StoryCreation.CreationStatus status);

    List<StoryCreation> findAllByOrderByCreatedAtDesc();
}
