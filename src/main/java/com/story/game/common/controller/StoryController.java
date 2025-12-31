package com.story.game.common.controller;

import com.story.game.common.entity.StoryData;
import com.story.game.common.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Tag(name = "Story", description = "스토리 검색 및 조회 API")
public class StoryController {

    private final StoryService storyService;

    @GetMapping("/search")
    @Operation(summary = "스토리 검색", description = "제목, 설명, 장르로 스토리를 검색합니다")
    public ResponseEntity<Page<StoryData>> searchStories(
            @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 기준 (popular, likes, latest)") @RequestParam(defaultValue = "latest") String sortBy) {

        Page<StoryData> stories = storyService.searchStories(keyword, page, size, sortBy);
        return ResponseEntity.ok(stories);
    }

    @GetMapping("/genre/{genre}")
    @Operation(summary = "장르별 스토리 조회", description = "특정 장르의 스토리를 조회합니다")
    public ResponseEntity<Page<StoryData>> getStoriesByGenre(
            @PathVariable String genre,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 기준 (popular, likes, latest)") @RequestParam(defaultValue = "latest") String sortBy) {

        Page<StoryData> stories = storyService.getStoriesByGenre(genre, page, size, sortBy);
        return ResponseEntity.ok(stories);
    }

    @GetMapping("/popular")
    @Operation(summary = "인기 스토리 조회", description = "조회수가 높은 스토리를 조회합니다")
    public ResponseEntity<Page<StoryData>> getPopularStories(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {

        Page<StoryData> stories = storyService.getPopularStories(page, size);
        return ResponseEntity.ok(stories);
    }

    @GetMapping("/most-liked")
    @Operation(summary = "좋아요 많은 스토리 조회", description = "좋아요가 많은 스토리를 조회합니다")
    public ResponseEntity<Page<StoryData>> getMostLikedStories(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {

        Page<StoryData> stories = storyService.getMostLikedStories(page, size);
        return ResponseEntity.ok(stories);
    }

    @GetMapping("/latest")
    @Operation(summary = "최신 스토리 조회", description = "최근에 생성된 스토리를 조회합니다")
    public ResponseEntity<Page<StoryData>> getLatestStories(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size) {

        Page<StoryData> stories = storyService.getLatestStories(page, size);
        return ResponseEntity.ok(stories);
    }

    @GetMapping
    @Operation(summary = "전체 스토리 조회", description = "모든 스토리를 페이징 처리하여 조회합니다")
    public ResponseEntity<Page<StoryData>> getAllStories(
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "정렬 기준 (popular, likes, latest)") @RequestParam(defaultValue = "latest") String sortBy) {

        Page<StoryData> stories = storyService.getAllStories(page, size, sortBy);
        return ResponseEntity.ok(stories);
    }
}
