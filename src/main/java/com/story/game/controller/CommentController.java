package com.story.game.controller;

import com.story.game.dto.CommentResponseDto;
import com.story.game.dto.CreateCommentRequestDto;
import com.story.game.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comment", description = "댓글 관련 API")
public class CommentController {

    private final CommentService commentService;

    @PostMapping
    @Operation(summary = "댓글 작성", description = "게시글에 댓글을 작성합니다")
    public ResponseEntity<CommentResponseDto> createComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequestDto request) {
        return ResponseEntity.ok(commentService.createComment(userDetails.getUsername(), postId, request));
    }

    @PutMapping("/{commentId}")
    @Operation(summary = "댓글 수정", description = "댓글을 수정합니다")
    public ResponseEntity<CommentResponseDto> updateComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @RequestBody String content) {
        return ResponseEntity.ok(commentService.updateComment(userDetails.getUsername(), commentId, content));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제", description = "댓글을 삭제합니다")
    public ResponseEntity<String> deleteComment(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        commentService.deleteComment(userDetails.getUsername(), commentId);
        return ResponseEntity.ok("Comment deleted successfully");
    }

    @GetMapping
    @Operation(summary = "댓글 목록 조회", description = "게시글의 댓글 목록을 조회합니다")
    public ResponseEntity<List<CommentResponseDto>> getComments(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getCommentsByPost(userDetails.getUsername(), postId));
    }

    @PostMapping("/{commentId}/like")
    @Operation(summary = "댓글 좋아요 토글", description = "댓글 좋아요를 추가/취소합니다")
    public ResponseEntity<String> toggleLike(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        commentService.toggleLike(userDetails.getUsername(), commentId);
        return ResponseEntity.ok("Like toggled successfully");
    }
}
