package com.story.game.common.service;

import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryDataRepository storyDataRepository;

    /**
     * 스토리 검색 (제목, 설명, 장르)
     */
    @Transactional(readOnly = true)
    public Page<StoryData> searchStories(String keyword, int page, int size, String sortBy) {
        Pageable pageable = createPageable(page, size, sortBy);

        if (keyword == null || keyword.isBlank()) {
            return storyDataRepository.findAll(pageable);
        }

        return storyDataRepository.searchByKeyword(keyword, pageable);
    }

    /**
     * 장르별 스토리 조회
     */
    @Transactional(readOnly = true)
    public Page<StoryData> getStoriesByGenre(String genre, int page, int size, String sortBy) {
        Pageable pageable = createPageable(page, size, sortBy);

        if ("popular".equals(sortBy)) {
            return storyDataRepository.findByGenreOrderByViewCountDesc(genre, pageable);
        }

        return storyDataRepository.findByGenre(genre, pageable);
    }

    /**
     * 인기 스토리 조회 (조회수 기준)
     */
    @Transactional(readOnly = true)
    public Page<StoryData> getPopularStories(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return storyDataRepository.findAllByOrderByViewCountDesc(pageable);
    }

    /**
     * 좋아요 많은 스토리 조회
     */
    @Transactional(readOnly = true)
    public Page<StoryData> getMostLikedStories(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return storyDataRepository.findAllByOrderByLikesCountDesc(pageable);
    }

    /**
     * 최신 스토리 조회
     */
    @Transactional(readOnly = true)
    public Page<StoryData> getLatestStories(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return storyDataRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 전체 스토리 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public Page<StoryData> getAllStories(int page, int size, String sortBy) {
        Pageable pageable = createPageable(page, size, sortBy);
        return storyDataRepository.findAll(pageable);
    }

    /**
     * Pageable 생성 헬퍼 메서드
     */
    private Pageable createPageable(int page, int size, String sortBy) {
        Sort sort = switch (sortBy) {
            case "popular" -> Sort.by(Sort.Direction.DESC, "viewCount");
            case "likes" -> Sort.by(Sort.Direction.DESC, "likesCount");
            case "latest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "createdAt"); // 기본값: 최신순
        };

        return PageRequest.of(page, size, sort);
    }
}
