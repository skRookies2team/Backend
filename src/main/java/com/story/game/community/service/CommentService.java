package com.story.game.community.service;

import com.story.game.community.dto.CommentResponseDto;
import com.story.game.community.dto.CreateCommentRequestDto;
import com.story.game.community.entity.Comment;
import com.story.game.community.entity.Like;
import com.story.game.community.entity.Post;
import com.story.game.auth.entity.User;
import com.story.game.community.repository.CommentRepository;
import com.story.game.community.repository.LikeRepository;
import com.story.game.community.repository.PostRepository;
import com.story.game.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public CommentResponseDto createComment(String username, Long postId, CreateCommentRequestDto request) {
        User user = getUserByUsername(username);
        Post post = getPostById(postId);

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = getCommentById(request.getParentId());
        }

        Comment comment = Comment.builder()
                .post(post)
                .author(user)
                .content(request.getContent())
                .parent(parent)
                .build();

        commentRepository.save(comment);

        // 게시글의 댓글 수 증가
        post.incrementCommentCount();
        postRepository.save(post);

        return CommentResponseDto.from(comment, false);
    }

    @Transactional
    public CommentResponseDto updateComment(String username, Long commentId, String content) {
        Comment comment = getCommentById(commentId);
        validateAuthor(comment, username);

        comment.updateContent(content);
        commentRepository.save(comment);

        User user = getUserByUsername(username);
        return CommentResponseDto.from(comment, isLiked(user, commentId));
    }

    @Transactional
    public void deleteComment(String username, Long commentId) {
        Comment comment = getCommentById(commentId);
        validateAuthor(comment, username);

        Post post = comment.getPost();
        post.decrementCommentCount();
        postRepository.save(post);

        commentRepository.delete(comment);
    }

    @Transactional(readOnly = true)
    public List<CommentResponseDto> getCommentsByPost(String username, Long postId) {
        Post post = getPostById(postId);
        List<Comment> topLevelComments = commentRepository.findByPostAndParentIsNullOrderByCreatedAtAsc(post);

        // 비로그인 사용자는 좋아요 정보 없이 반환
        if (username == null) {
            return topLevelComments.stream()
                    .map(comment -> {
                        CommentResponseDto dto = CommentResponseDto.from(comment, false);

                        // 대댓글 로드
                        List<Comment> replies = commentRepository.findByParentOrderByCreatedAtAsc(comment);
                        List<CommentResponseDto> replyDtos = replies.stream()
                                .map(reply -> CommentResponseDto.from(reply, false))
                                .collect(Collectors.toList());

                        return CommentResponseDto.builder()
                                .commentId(dto.getCommentId())
                                .authorUsername(dto.getAuthorUsername())
                                .authorNickname(dto.getAuthorNickname())
                                .content(dto.getContent())
                                .parentId(dto.getParentId())
                                .likeCount(dto.getLikeCount())
                                .isLiked(false)
                                .createdAt(dto.getCreatedAt())
                                .updatedAt(dto.getUpdatedAt())
                                .replies(replyDtos)
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        User user = getUserByUsername(username);

        return topLevelComments.stream()
                .map(comment -> {
                    CommentResponseDto dto = CommentResponseDto.from(comment, isLiked(user, comment.getId()));

                    // 대댓글 로드
                    List<Comment> replies = commentRepository.findByParentOrderByCreatedAtAsc(comment);
                    List<CommentResponseDto> replyDtos = replies.stream()
                            .map(reply -> CommentResponseDto.from(reply, isLiked(user, reply.getId())))
                            .collect(Collectors.toList());

                    return CommentResponseDto.builder()
                            .commentId(dto.getCommentId())
                            .authorUsername(dto.getAuthorUsername())
                            .authorNickname(dto.getAuthorNickname())
                            .content(dto.getContent())
                            .parentId(dto.getParentId())
                            .likeCount(dto.getLikeCount())
                            .isLiked(dto.getIsLiked())
                            .createdAt(dto.getCreatedAt())
                            .updatedAt(dto.getUpdatedAt())
                            .replies(replyDtos)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void toggleLike(String username, Long commentId) {
        User user = getUserByUsername(username);
        Comment comment = getCommentById(commentId);

        if (likeRepository.existsByUserAndTargetTypeAndTargetId(user, Like.TargetType.COMMENT, commentId)) {
            likeRepository.deleteByUserAndTargetTypeAndTargetId(user, Like.TargetType.COMMENT, commentId);
            comment.decrementLikeCount();
        } else {
            Like like = Like.builder()
                    .user(user)
                    .targetType(Like.TargetType.COMMENT)
                    .targetId(commentId)
                    .build();
            likeRepository.save(like);
            comment.incrementLikeCount();
        }

        commentRepository.save(comment);
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
    }

    private Comment getCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
    }

    private void validateAuthor(Comment comment, String username) {
        if (!comment.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("Not authorized to modify this comment");
        }
    }

    private boolean isLiked(User user, Long commentId) {
        return likeRepository.existsByUserAndTargetTypeAndTargetId(user, Like.TargetType.COMMENT, commentId);
    }
}
