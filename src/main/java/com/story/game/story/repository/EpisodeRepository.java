package com.story.game.story.repository;

import com.story.game.creation.entity.StoryCreation;
import com.story.game.story.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, UUID> {
    Optional<Episode> findByStoryAndOrder(StoryCreation story, int order);
    List<Episode> findAllByStory(StoryCreation story);
    List<Episode> findAllByStoryOrderByOrderAsc(StoryCreation story);
}
