package com.story.game.common.util;

import com.story.game.auth.entity.User;
import com.story.game.community.entity.Bookmark;
import com.story.game.community.entity.Like;
import com.story.game.community.repository.BookmarkRepository;
import com.story.game.community.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 커뮤니티 관련 공통 유틸리티
 * 좋아요, 북마크 등의 중복 로직을 처리
 */
@Component
@RequiredArgsConstructor
public class CommunityUtils {

    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;

    /**
     * 게시글 좋아요 여부 확인
     */
    public boolean isPostLiked(User user, Long postId) {
        if (user == null) return false;
        return likeRepository.existsByUserAndTargetTypeAndTargetId(
                user, Like.TargetType.POST, postId);
    }

    /**
     * 댓글 좋아요 여부 확인
     */
    public boolean isCommentLiked(User user, Long commentId) {
        if (user == null) return false;
        return likeRepository.existsByUserAndTargetTypeAndTargetId(
                user, Like.TargetType.COMMENT, commentId);
    }

    /**
     * 스토리 좋아요 여부 확인
     */
    public boolean isStoryLiked(User user, Long storyId) {
        if (user == null) return false;
        return likeRepository.existsByUserAndTargetTypeAndTargetId(
                user, Like.TargetType.STORY, storyId);
    }

    /**
     * 게시글 북마크 여부 확인
     */
    public boolean isPostBookmarked(User user, Long postId) {
        if (user == null) return false;
        return bookmarkRepository.existsByUserAndTargetTypeAndTargetId(
                user, Bookmark.TargetType.POST, postId);
    }

    /**
     * 스토리 북마크 여부 확인
     */
    public boolean isStoryBookmarked(User user, Long storyId) {
        if (user == null) return false;
        return bookmarkRepository.existsByUserAndTargetTypeAndTargetId(
                user, Bookmark.TargetType.STORY, storyId);
    }
}
