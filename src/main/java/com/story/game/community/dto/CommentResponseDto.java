package com.story.game.community.dto;

import com.story.game.community.entity.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponseDto {
    private Long commentId;
    private String authorUsername;
    private String authorNickname;
    private String content;
    private Long parentId;
    private Integer likeCount;
    private Boolean isLiked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CommentResponseDto> replies;

    public static CommentResponseDto from(Comment comment, Boolean isLiked) {
        return CommentResponseDto.builder()
                .commentId(comment.getId())
                .authorUsername(comment.getAuthor().getUsername())
                .authorNickname(comment.getAuthor().getNickname())
                .content(comment.getContent())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .likeCount(comment.getLikeCount())
                .isLiked(isLiked)
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
