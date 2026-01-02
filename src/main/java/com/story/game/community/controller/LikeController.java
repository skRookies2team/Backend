package com.story.game.community.controller;

import com.story.game.common.entity.StoryData;
import com.story.game.community.dto.LikeStatusResponseDto;
import com.story.game.community.dto.LikeToggleResponseDto;
import com.story.game.community.service.LikeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/likes")
@RequiredArgsConstructor
@Tag(name = "Like", description = "좋아요 관련 API")
public class LikeController {

    private final LikeService likeService;

    @PostMapping("/stories/{storyDataId}")
    @Operation(
            summary = "스토리 좋아요 토글",
            description = "스토리에 좋아요를 추가하거나 취소합니다",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "성공",
                            content = @Content(schema = @Schema(implementation = LikeToggleResponseDto.class))
                    )
            }
    )
    public ResponseEntity<LikeToggleResponseDto> toggleStoryLike(
            @PathVariable Long storyDataId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();
        boolean liked = likeService.toggleStoryLike(storyDataId, username);

        LikeToggleResponseDto response = LikeToggleResponseDto.builder()
                .liked(liked)
                .message(liked ? "좋아요가 추가되었습니다" : "좋아요가 취소되었습니다")
                .username(username)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stories/{storyDataId}/status")
    @Operation(
            summary = "스토리 좋아요 상태 조회",
            description = "현재 사용자가 해당 스토리에 좋아요를 눌렀는지 확인합니다",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "성공",
                            content = @Content(schema = @Schema(implementation = LikeStatusResponseDto.class))
                    )
            }
    )
    public ResponseEntity<LikeStatusResponseDto> getStoryLikeStatus(
            @PathVariable Long storyDataId,
            @AuthenticationPrincipal UserDetails userDetails) {

        String username = userDetails.getUsername();
        boolean liked = likeService.isStoryLikedByUser(storyDataId, username);

        LikeStatusResponseDto response = LikeStatusResponseDto.builder()
                .liked(liked)
                .username(username)
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stories")
    @Operation(summary = "좋아요한 스토리 목록 조회", description = "사용자가 좋아요 누른 스토리 목록을 조회합니다")
    public ResponseEntity<List<StoryData>> getLikedStories(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<StoryData> stories = likeService.getLikedStories(userDetails.getUsername());

        return ResponseEntity.ok(stories);
    }
}
