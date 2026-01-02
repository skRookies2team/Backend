package com.story.game.community.repository;

import com.story.game.community.entity.StoryReview;
import com.story.game.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StoryReviewRepository extends JpaRepository<StoryReview, Long> {
    // N+1 최적화: 작성자 정보를 함께 로드
    @EntityGraph(attributePaths = {"author"})
    Page<StoryReview> findByStoryDataIdOrderByCreatedAtDesc(Long storyDataId, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Optional<StoryReview> findByAuthorAndStoryDataId(User author, Long storyDataId);

    boolean existsByAuthorAndStoryDataId(User author, Long storyDataId);

    @Query("SELECT AVG(r.rating) FROM StoryReview r WHERE r.storyDataId = :storyDataId")
    Double getAverageRatingByStoryDataId(Long storyDataId);

    long countByStoryDataId(Long storyDataId);
}
