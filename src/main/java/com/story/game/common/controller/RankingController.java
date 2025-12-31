package com.story.game.common.controller;

import com.story.game.common.entity.StoryData;
import com.story.game.common.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
@Tag(name = "Ranking", description = "랭킹 시스템 API")
public class RankingController {

    private final RankingService rankingService;

    @GetMapping("/users")
    @Operation(summary = "유저 랭킹 조회", description = "업적 포인트 기반 유저 랭킹을 조회합니다")
    public ResponseEntity<List<Map<String, Object>>> getUserRanking(
            @Parameter(description = "조회할 랭킹 수") @RequestParam(defaultValue = "10") int limit) {

        List<Map<String, Object>> ranking = rankingService.getUserRankingByAchievements(limit);
        return ResponseEntity.ok(ranking);
    }

    @GetMapping("/creators")
    @Operation(summary = "인기 작가 랭킹 조회", description = "작성한 스토리의 인기도 기반 작가 랭킹을 조회합니다")
    public ResponseEntity<List<Map<String, Object>>> getCreatorsRanking(
            @Parameter(description = "조회할 랭킹 수") @RequestParam(defaultValue = "10") int limit) {

        List<Map<String, Object>> ranking = rankingService.getPopularCreatorsRanking(limit);
        return ResponseEntity.ok(ranking);
    }

    @GetMapping("/stories/weekly")
    @Operation(summary = "이번 주 인기 스토리", description = "이번 주 인기 스토리를 조회합니다")
    public ResponseEntity<List<StoryData>> getWeeklyPopularStories(
            @Parameter(description = "조회할 스토리 수") @RequestParam(defaultValue = "10") int limit) {

        List<StoryData> stories = rankingService.getWeeklyPopularStories(limit);
        return ResponseEntity.ok(stories);
    }
}
