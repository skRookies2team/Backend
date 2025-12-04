package com.story.game.story.repository;

import com.story.game.creation.entity.StoryCreation;
import com.story.game.story.entity.StoryNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StoryNodeRepository extends JpaRepository<StoryNode, UUID> {
    long countByEpisode_Story(StoryCreation story);
}
