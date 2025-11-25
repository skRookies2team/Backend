package com.story.game.community.controller;

import com.story.game.community.dto.CreateReviewRequestDto;
import com.story.game.community.dto.ReviewResponseDto;
import com.story.game.community.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Review", description = "스토리 리뷰 관련 API")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    @Operation(summary = "리뷰 작성", description = "스토리에 대한 리뷰를 작성합니다")
    public ResponseEntity<ReviewResponseDto> createReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateReviewRequestDto request) {
        return ResponseEntity.ok(reviewService.createReview(userDetails.getUsername(), request));
    }

    @PutMapping("/{reviewId}")
    @Operation(summary = "리뷰 수정", description = "리뷰를 수정합니다")
    public ResponseEntity<ReviewResponseDto> updateReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long reviewId,
            @Valid @RequestBody CreateReviewRequestDto request) {
        return ResponseEntity.ok(reviewService.updateReview(userDetails.getUsername(), reviewId, request));
    }

    @DeleteMapping("/{reviewId}")
    @Operation(summary = "리뷰 삭제", description = "리뷰를 삭제합니다")
    public ResponseEntity<String> deleteReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long reviewId) {
        reviewService.deleteReview(userDetails.getUsername(), reviewId);
        return ResponseEntity.ok("Review deleted successfully");
    }

    @GetMapping("/story/{storyDataId}")
    @Operation(summary = "스토리별 리뷰 목록 조회", description = "특정 스토리의 리뷰 목록을 조회합니다")
    public ResponseEntity<Page<ReviewResponseDto>> getReviewsByStory(
            @PathVariable Long storyDataId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(reviewService.getReviewsByStory(storyDataId, pageable));
    }

    @GetMapping("/story/{storyDataId}/stats")
    @Operation(summary = "스토리 평점 통계 조회", description = "스토리의 평균 평점과 리뷰 수를 조회합니다")
    public ResponseEntity<Map<String, Object>> getStoryRatingStats(@PathVariable Long storyDataId) {
        return ResponseEntity.ok(reviewService.getStoryRatingStats(storyDataId));
    }

    @GetMapping("/story/{storyDataId}/me")
    @Operation(summary = "내 리뷰 조회", description = "특정 스토리에 대한 내 리뷰를 조회합니다")
    public ResponseEntity<ReviewResponseDto> getMyReviewForStory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long storyDataId) {
        ReviewResponseDto review = reviewService.getUserReviewForStory(userDetails.getUsername(), storyDataId);
        if (review == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(review);
    }
}
