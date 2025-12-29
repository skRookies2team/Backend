package com.story.game.community.service;

import com.story.game.community.dto.CreateReviewRequestDto;
import com.story.game.community.dto.ReviewResponseDto;
import com.story.game.community.entity.StoryReview;
import com.story.game.auth.entity.User;
import com.story.game.community.repository.StoryReviewRepository;
import com.story.game.auth.repository.UserRepository;
import com.story.game.common.entity.StoryData;
import com.story.game.common.exception.ResourceNotFoundException;
import com.story.game.common.repository.StoryDataRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final StoryReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final StoryDataRepository storyDataRepository;

    @Transactional
    public ReviewResponseDto createReview(String username, CreateReviewRequestDto request) {
        User user = getUserByUsername(username);

        // 이미 리뷰를 작성했는지 확인
        if (reviewRepository.existsByAuthorAndStoryDataId(user, request.getStoryDataId())) {
            throw new IllegalArgumentException("You have already reviewed this story");
        }

        StoryReview review = StoryReview.builder()
                .author(user)
                .storyDataId(request.getStoryDataId())
                .rating(request.getRating())
                .content(request.getContent())
                .build();

        reviewRepository.save(review);

        return ReviewResponseDto.from(review);
    }

    @Transactional
    public ReviewResponseDto updateReview(String username, Long reviewId, CreateReviewRequestDto request) {
        StoryReview review = getReviewById(reviewId);
        validateAuthor(review, username);

        review.updateReview(request.getRating(), request.getContent());
        reviewRepository.save(review);

        return ReviewResponseDto.from(review);
    }

    @Transactional
    public void deleteReview(String username, Long reviewId) {
        StoryReview review = getReviewById(reviewId);
        validateAuthor(review, username);

        reviewRepository.delete(review);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponseDto> getReviewsByStory(Long storyDataId, Pageable pageable) {
        validateStoryDataExists(storyDataId);
        return reviewRepository.findByStoryDataIdOrderByCreatedAtDesc(storyDataId, pageable)
                .map(ReviewResponseDto::from);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStoryRatingStats(Long storyDataId) {
        validateStoryDataExists(storyDataId);
        Double averageRating = reviewRepository.getAverageRatingByStoryDataId(storyDataId);
        long totalReviews = reviewRepository.countByStoryDataId(storyDataId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("averageRating", averageRating != null ? averageRating : 0.0);
        stats.put("totalReviews", totalReviews);

        return stats;
    }

    @Transactional(readOnly = true)
    public ReviewResponseDto getUserReviewForStory(String username, Long storyDataId) {
        User user = getUserByUsername(username);

        return reviewRepository.findByAuthorAndStoryDataId(user, storyDataId)
                .map(ReviewResponseDto::from)
                .orElse(null);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private StoryReview getReviewById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
    }

    private void validateAuthor(StoryReview review, String username) {
        if (!review.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("Not authorized to modify this review");
        }
    }

    private void validateStoryDataExists(Long storyDataId) {
        storyDataRepository.findById(storyDataId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyDataId));
    }
}
