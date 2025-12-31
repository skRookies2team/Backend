package com.story.game.community.controller;

import com.story.game.auth.entity.User;
import com.story.game.common.entity.StoryData;
import com.story.game.community.service.BookmarkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookmarks")
@RequiredArgsConstructor
@Tag(name = "Bookmark", description = "북마크 관련 API")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping("/stories/{storyDataId}")
    @Operation(summary = "스토리 북마크 토글", description = "스토리를 북마크에 추가하거나 취소합니다")
    public ResponseEntity<Map<String, Object>> toggleStoryBookmark(
            @PathVariable Long storyDataId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = (User) userDetails;
        boolean bookmarked = bookmarkService.toggleStoryBookmark(storyDataId, user);

        return ResponseEntity.ok(Map.of(
                "bookmarked", bookmarked,
                "message", bookmarked ? "북마크에 추가되었습니다" : "북마크가 취소되었습니다"
        ));
    }

    @GetMapping("/stories")
    @Operation(summary = "북마크한 스토리 목록 조회", description = "사용자가 북마크한 스토리 목록을 조회합니다")
    public ResponseEntity<List<StoryData>> getBookmarkedStories(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = (User) userDetails;
        List<StoryData> stories = bookmarkService.getBookmarkedStories(user);

        return ResponseEntity.ok(stories);
    }

    @GetMapping("/stories/{storyDataId}/status")
    @Operation(summary = "스토리 북마크 상태 조회", description = "현재 사용자가 해당 스토리를 북마크했는지 확인합니다")
    public ResponseEntity<Map<String, Boolean>> getStoryBookmarkStatus(
            @PathVariable Long storyDataId,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = (User) userDetails;
        boolean bookmarked = bookmarkService.isStoryBookmarked(storyDataId, user);

        return ResponseEntity.ok(Map.of("bookmarked", bookmarked));
    }
}
