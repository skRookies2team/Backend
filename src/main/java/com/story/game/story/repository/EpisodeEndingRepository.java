package com.story.game.story.repository;

import com.story.game.story.entity.EpisodeEnding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EpisodeEndingRepository extends JpaRepository<EpisodeEnding, UUID> {
}
