package com.story.game.repository;

import com.story.game.entity.StoryReview;
import com.story.game.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoryReviewRepository extends JpaRepository<StoryReview, Long> {
    Page<StoryReview> findByStoryDataIdOrderByCreatedAtDesc(Long storyDataId, Pageable pageable);
    Optional<StoryReview> findByAuthorAndStoryDataId(User author, Long storyDataId);
    boolean existsByAuthorAndStoryDataId(User author, Long storyDataId);

    @Query("SELECT AVG(r.rating) FROM StoryReview r WHERE r.storyDataId = :storyDataId")
    Double getAverageRatingByStoryDataId(Long storyDataId);

    long countByStoryDataId(Long storyDataId);
}
