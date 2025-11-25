package com.story.game.community.controller;

import com.story.game.community.dto.CreatePostRequestDto;
import com.story.game.community.dto.PostResponseDto;
import com.story.game.community.entity.Post;
import com.story.game.community.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Post", description = "게시판 관련 API")
public class PostController {

    private final PostService postService;

    @PostMapping
    @Operation(summary = "게시글 작성", description = "새로운 게시글을 작성합니다")
    public ResponseEntity<PostResponseDto> createPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreatePostRequestDto request) {
        return ResponseEntity.ok(postService.createPost(userDetails.getUsername(), request));
    }

    @PutMapping("/{postId}")
    @Operation(summary = "게시글 수정", description = "게시글을 수정합니다")
    public ResponseEntity<PostResponseDto> updatePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId,
            @Valid @RequestBody CreatePostRequestDto request) {
        return ResponseEntity.ok(postService.updatePost(userDetails.getUsername(), postId, request));
    }

    @DeleteMapping("/{postId}")
    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다")
    public ResponseEntity<String> deletePost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId) {
        postService.deletePost(userDetails.getUsername(), postId);
        return ResponseEntity.ok("Post deleted successfully");
    }

    @GetMapping("/{postId}")
    @Operation(summary = "게시글 상세 조회", description = "게시글 상세 정보를 조회합니다")
    public ResponseEntity<PostResponseDto> getPost(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId) {
        return ResponseEntity.ok(postService.getPost(userDetails.getUsername(), postId));
    }

    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "게시글 목록을 조회합니다")
    public ResponseEntity<Page<PostResponseDto>> getPosts(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getPosts(userDetails.getUsername(), pageable));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "타입별 게시글 조회", description = "특정 타입의 게시글을 조회합니다")
    public ResponseEntity<Page<PostResponseDto>> getPostsByType(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Post.PostType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.getPostsByType(userDetails.getUsername(), type, pageable));
    }

    @GetMapping("/search")
    @Operation(summary = "게시글 검색", description = "키워드로 게시글을 검색합니다")
    public ResponseEntity<Page<PostResponseDto>> searchPosts(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(postService.searchPosts(userDetails.getUsername(), keyword, pageable));
    }

    @PostMapping("/{postId}/like")
    @Operation(summary = "게시글 좋아요 토글", description = "게시글 좋아요를 추가/취소합니다")
    public ResponseEntity<String> toggleLike(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId) {
        postService.toggleLike(userDetails.getUsername(), postId);
        return ResponseEntity.ok("Like toggled successfully");
    }

    @PostMapping("/{postId}/bookmark")
    @Operation(summary = "게시글 북마크 토글", description = "게시글 북마크를 추가/취소합니다")
    public ResponseEntity<String> toggleBookmark(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long postId) {
        postService.toggleBookmark(userDetails.getUsername(), postId);
        return ResponseEntity.ok("Bookmark toggled successfully");
    }
}
