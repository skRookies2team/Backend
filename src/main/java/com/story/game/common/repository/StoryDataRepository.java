package com.story.game.common.repository;

import com.story.game.common.entity.StoryData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoryDataRepository extends JpaRepository<StoryData, Long> {
}
