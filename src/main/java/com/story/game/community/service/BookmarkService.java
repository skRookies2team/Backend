package com.story.game.community.service;

import com.story.game.auth.entity.User;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.community.entity.Bookmark;
import com.story.game.community.repository.BookmarkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final StoryDataRepository storyDataRepository;

    /**
     * 스토리 북마크 토글 (추가/취소)
     */
    @Transactional
    public boolean toggleStoryBookmark(Long storyDataId, User user) {
        // 스토리 존재 확인
        storyDataRepository.findById(storyDataId)
                .orElseThrow(() -> new IllegalArgumentException("Story not found: " + storyDataId));

        boolean exists = bookmarkRepository.existsByUserAndTargetTypeAndTargetId(
                user, Bookmark.TargetType.STORY, storyDataId);

        if (exists) {
            // 북마크 취소
            bookmarkRepository.deleteByUserAndTargetTypeAndTargetId(
                    user, Bookmark.TargetType.STORY, storyDataId);
            log.info("Story bookmark removed: storyDataId={}, userId={}", storyDataId, user.getId());
            return false;
        } else {
            // 북마크 추가
            Bookmark bookmark = Bookmark.builder()
                    .user(user)
                    .targetType(Bookmark.TargetType.STORY)
                    .targetId(storyDataId)
                    .build();
            bookmarkRepository.save(bookmark);
            log.info("Story bookmark added: storyDataId={}, userId={}", storyDataId, user.getId());
            return true;
        }
    }

    /**
     * 사용자의 북마크한 스토리 목록 조회
     */
    @Transactional(readOnly = true)
    public List<StoryData> getBookmarkedStories(User user) {
        List<Bookmark> bookmarks = bookmarkRepository.findByUserAndTargetType(user, Bookmark.TargetType.STORY);

        return bookmarks.stream()
                .map(bookmark -> storyDataRepository.findById(bookmark.getTargetId()).orElse(null))
                .filter(storyData -> storyData != null)
                .collect(Collectors.toList());
    }

    /**
     * 사용자가 특정 스토리를 북마크했는지 확인
     */
    @Transactional(readOnly = true)
    public boolean isStoryBookmarked(Long storyDataId, User user) {
        return bookmarkRepository.existsByUserAndTargetTypeAndTargetId(
                user, Bookmark.TargetType.STORY, storyDataId);
    }
}
