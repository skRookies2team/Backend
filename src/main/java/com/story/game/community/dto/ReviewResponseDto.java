package com.story.game.community.dto;

import com.story.game.community.entity.StoryReview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewResponseDto {
    private Long reviewId;
    private String authorUsername;
    private String authorNickname;
    private String authorProfileImageUrl;
    private Long storyDataId;
    private Integer rating;
    private String content;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ReviewResponseDto from(StoryReview review) {
        return ReviewResponseDto.builder()
                .reviewId(review.getId())
                .authorUsername(review.getAuthor().getUsername())
                .authorNickname(review.getAuthor().getNickname())
                .authorProfileImageUrl(review.getAuthor().getProfileImageUrl())
                .storyDataId(review.getStoryDataId())
                .rating(review.getRating())
                .content(review.getContent())
                .likeCount(review.getLikeCount())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
