package com.story.game.community.dto;

import com.story.game.community.entity.Post;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostResponseDto {
    private Long postId;
    private String authorUsername;
    private String authorNickname;
    private String title;
    private String content;
    private String type;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Boolean isLiked;
    private Boolean isBookmarked;
    private List<MediaDto> mediaFiles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PostResponseDto from(Post post, Boolean isLiked, Boolean isBookmarked) {
        List<MediaDto> mediaFiles = post.getMediaFiles().stream()
                .sorted((m1, m2) -> m1.getMediaOrder().compareTo(m2.getMediaOrder()))
                .map(MediaDto::from)
                .collect(Collectors.toList());

        return PostResponseDto.builder()
                .postId(post.getId())
                .authorUsername(post.getAuthor().getUsername())
                .authorNickname(post.getAuthor().getNickname())
                .title(post.getTitle())
                .content(post.getContent())
                .type(post.getType().name())
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isLiked(isLiked)
                .isBookmarked(isBookmarked)
                .mediaFiles(mediaFiles)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
