package com.story.game.community.service;

import com.story.game.auth.entity.User;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.community.entity.Like;
import com.story.game.community.repository.LikeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final StoryDataRepository storyDataRepository;

    /**
     * 스토리 좋아요 토글 (추가/취소)
     */
    @Transactional
    public boolean toggleStoryLike(Long storyDataId, User user) {
        StoryData storyData = storyDataRepository.findById(storyDataId)
                .orElseThrow(() -> new IllegalArgumentException("Story not found: " + storyDataId));

        boolean exists = likeRepository.existsByUserAndTargetTypeAndTargetId(
                user, Like.TargetType.STORY, storyDataId);

        if (exists) {
            // 좋아요 취소
            likeRepository.deleteByUserAndTargetTypeAndTargetId(
                    user, Like.TargetType.STORY, storyDataId);
            storyData.decrementLikesCount();
            storyDataRepository.save(storyData);
            log.info("Story like removed: storyDataId={}, userId={}, likesCount={}",
                    storyDataId, user.getId(), storyData.getLikesCount());
            return false;
        } else {
            // 좋아요 추가
            Like like = Like.builder()
                    .user(user)
                    .targetType(Like.TargetType.STORY)
                    .targetId(storyDataId)
                    .build();
            likeRepository.save(like);
            storyData.incrementLikesCount();
            storyDataRepository.save(storyData);
            log.info("Story like added: storyDataId={}, userId={}, likesCount={}",
                    storyDataId, user.getId(), storyData.getLikesCount());
            return true;
        }
    }

    /**
     * 사용자가 특정 스토리에 좋아요를 눌렀는지 확인
     */
    @Transactional(readOnly = true)
    public boolean isStoryLikedByUser(Long storyDataId, User user) {
        return likeRepository.existsByUserAndTargetTypeAndTargetId(
                user, Like.TargetType.STORY, storyDataId);
    }
}
