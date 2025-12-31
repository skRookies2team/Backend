package com.story.game.common.repository;

import com.story.game.common.entity.StoryData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryDataRepository extends JpaRepository<StoryData, Long> {

    // 제목으로 검색 (페이징)
    Page<StoryData> findByTitleContaining(String title, Pageable pageable);

    // 장르로 검색 (페이징)
    Page<StoryData> findByGenre(String genre, Pageable pageable);

    // 제목 또는 설명으로 검색 (페이징)
    Page<StoryData> findByTitleContainingOrDescriptionContaining(String title, String description, Pageable pageable);

    // 조회수 순 정렬 (인기 스토리)
    Page<StoryData> findAllByOrderByViewCountDesc(Pageable pageable);

    // 좋아요 순 정렬
    Page<StoryData> findAllByOrderByLikesCountDesc(Pageable pageable);

    // 최신순 정렬
    Page<StoryData> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 장르별 인기 스토리
    Page<StoryData> findByGenreOrderByViewCountDesc(String genre, Pageable pageable);

    // 전체 검색 (제목, 설명, 장르)
    @Query("SELECT s FROM StoryData s WHERE " +
           "s.title LIKE %:keyword% OR " +
           "s.description LIKE %:keyword% OR " +
           "s.genre LIKE %:keyword%")
    Page<StoryData> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
