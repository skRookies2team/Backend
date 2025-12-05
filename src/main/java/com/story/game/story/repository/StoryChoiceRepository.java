package com.story.game.story.repository;

import com.story.game.story.entity.StoryChoice;
import com.story.game.story.entity.StoryNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StoryChoiceRepository extends JpaRepository<StoryChoice, UUID> {
    List<StoryChoice> findBySourceNodeOrderByChoiceOrderAsc(StoryNode sourceNode);
}
