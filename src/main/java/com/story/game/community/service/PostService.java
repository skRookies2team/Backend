package com.story.game.community.service;

import com.story.game.community.dto.CreatePostRequestDto;
import com.story.game.community.dto.PostMediaUploadResponseDto;
import com.story.game.community.dto.PostResponseDto;
import com.story.game.community.entity.Bookmark;
import com.story.game.community.entity.Like;
import com.story.game.community.entity.Post;
import com.story.game.community.entity.PostMedia;
import com.story.game.auth.entity.User;
import com.story.game.community.repository.BookmarkRepository;
import com.story.game.community.repository.LikeRepository;
import com.story.game.community.repository.PostMediaRepository;
import com.story.game.community.repository.PostRepository;
import com.story.game.auth.repository.UserRepository;
import com.story.game.common.exception.ExternalServiceException;
import com.story.game.infrastructure.config.FileUploadProperties;
import com.story.game.infrastructure.s3.S3Service;
import com.story.game.achievement.service.AchievementService;
import com.story.game.common.util.XssUtils;
import com.story.game.common.util.CommunityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PostMediaRepository postMediaRepository;
    private final S3Service s3Service;
    private final FileUploadProperties uploadProperties;
    private final AchievementService achievementService;
    private final CommunityUtils communityUtils;

    @Transactional
    public PostResponseDto createPost(String username, CreatePostRequestDto request) {
        User user = getUserByUsername(username);

        // XSS 필터링 적용
        String sanitizedTitle = XssUtils.sanitize(request.getTitle());
        String sanitizedContent = XssUtils.sanitize(request.getContent());

        Post post = Post.builder()
                .author(user)
                .title(sanitizedTitle)
                .content(sanitizedContent)
                .type(request.getType())
                .build();

        postRepository.save(post);

        // Update user achievements after post creation
        try {
            achievementService.checkAndUpdateAchievements(user);
            log.info("Achievement progress updated for user {} after post creation", user.getUsername());
        } catch (Exception e) {
            log.error("Failed to update achievements for user {}: {}", user.getUsername(), e.getMessage(), e);
            // Continue anyway - achievement update failure shouldn't break post creation
        }

        return PostResponseDto.from(post, false, false);
    }

    @Transactional
    public PostResponseDto updatePost(String username, Long postId, CreatePostRequestDto request) {
        Post post = getPostById(postId);
        validateAuthor(post, username);

        // XSS 필터링 적용
        String sanitizedTitle = XssUtils.sanitize(request.getTitle());
        String sanitizedContent = XssUtils.sanitize(request.getContent());

        post.updatePost(sanitizedTitle, sanitizedContent);
        postRepository.save(post);

        User user = getUserByUsername(username);
        return PostResponseDto.from(post,
                isLiked(user, postId),
                isBookmarked(user, postId));
    }

    @Transactional
    public void deletePost(String username, Long postId) {
        Post post = getPostById(postId);
        validateAuthor(post, username);

        postRepository.delete(post);
    }

    @Transactional
    public PostResponseDto getPost(String username, Long postId) {
        Post post = getPostById(postId);
        post.incrementViewCount();
        postRepository.save(post);

        // 비로그인 사용자는 좋아요/북마크 정보 없이 반환
        if (username == null) {
            return PostResponseDto.from(post, false, false);
        }

        User user = getUserByUsername(username);
        return PostResponseDto.from(post,
                isLiked(user, postId),
                isBookmarked(user, postId));
    }

    @Transactional(readOnly = true)
    public Page<PostResponseDto> getPosts(String username, Pageable pageable) {
        // 비로그인 사용자는 좋아요/북마크 정보 없이 반환
        if (username == null) {
            return postRepository.findAllByOrderByCreatedAtDesc(pageable)
                    .map(post -> PostResponseDto.from(post, false, false));
        }

        User user = getUserByUsername(username);
        return postRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(post -> PostResponseDto.from(post,
                        isLiked(user, post.getId()),
                        isBookmarked(user, post.getId())));
    }

    @Transactional(readOnly = true)
    public Page<PostResponseDto> getPostsByType(String username, Post.PostType type, Pageable pageable) {
        // 비로그인 사용자는 좋아요/북마크 정보 없이 반환
        if (username == null) {
            return postRepository.findByTypeOrderByCreatedAtDesc(type, pageable)
                    .map(post -> PostResponseDto.from(post, false, false));
        }

        User user = getUserByUsername(username);
        return postRepository.findByTypeOrderByCreatedAtDesc(type, pageable)
                .map(post -> PostResponseDto.from(post,
                        isLiked(user, post.getId()),
                        isBookmarked(user, post.getId())));
    }

    @Transactional(readOnly = true)
    public Page<PostResponseDto> searchPosts(String username, String keyword, Pageable pageable) {
        // 비로그인 사용자는 좋아요/북마크 정보 없이 반환
        if (username == null) {
            return postRepository.findByTitleContainingOrContentContaining(keyword, keyword, pageable)
                    .map(post -> PostResponseDto.from(post, false, false));
        }

        User user = getUserByUsername(username);
        return postRepository.findByTitleContainingOrContentContaining(keyword, keyword, pageable)
                .map(post -> PostResponseDto.from(post,
                        isLiked(user, post.getId()),
                        isBookmarked(user, post.getId())));
    }

    @Transactional
    public void toggleLike(String username, Long postId) {
        User user = getUserByUsername(username);
        Post post = getPostById(postId);

        if (likeRepository.existsByUserAndTargetTypeAndTargetId(user, Like.TargetType.POST, postId)) {
            likeRepository.deleteByUserAndTargetTypeAndTargetId(user, Like.TargetType.POST, postId);
            post.decrementLikeCount();
        } else {
            Like like = Like.builder()
                    .user(user)
                    .targetType(Like.TargetType.POST)
                    .targetId(postId)
                    .build();
            likeRepository.save(like);
            post.incrementLikeCount();
        }

        postRepository.save(post);
    }

    @Transactional
    public void toggleBookmark(String username, Long postId) {
        User user = getUserByUsername(username);
        getPostById(postId); // 존재 여부 확인

        if (bookmarkRepository.existsByUserAndTargetTypeAndTargetId(user, Bookmark.TargetType.POST, postId)) {
            bookmarkRepository.deleteByUserAndTargetTypeAndTargetId(user, Bookmark.TargetType.POST, postId);
        } else {
            Bookmark bookmark = Bookmark.builder()
                    .user(user)
                    .targetType(Bookmark.TargetType.POST)
                    .targetId(postId)
                    .build();
            bookmarkRepository.save(bookmark);
        }
    }

    @Transactional
    public PostMediaUploadResponseDto uploadPostMedia(String username, Long postId, MultipartFile mediaFile) {
        User user = getUserByUsername(username);
        Post post = getPostById(postId);

        // 작성자 확인
        validateAuthor(post, username);

        // 파일 검증
        if (mediaFile.isEmpty()) {
            throw new IllegalArgumentException("Media file is required");
        }

        String contentType = mediaFile.getContentType();
        if (contentType == null) {
            throw new IllegalArgumentException("Content type is required");
        }

        // 미디어 타입 결정
        PostMedia.MediaType mediaType;
        long maxSize;
        int maxCount;
        String folder;

        if (contentType.startsWith("image/")) {
            mediaType = PostMedia.MediaType.IMAGE;
            maxSize = uploadProperties.getMaxSize().getImage();
            maxCount = 10; // 이미지 최대 10개
            folder = "post-images";
        } else if (contentType.startsWith("video/")) {
            mediaType = PostMedia.MediaType.VIDEO;
            maxSize = uploadProperties.getMaxSize().getVideo();
            maxCount = 3; // 동영상 최대 3개
            folder = "post-videos";
        } else {
            throw new IllegalArgumentException("Only image and video files are allowed");
        }

        // 미디어 타입별 개수 제한 확인
        Long currentCount = postMediaRepository.countByPostAndMediaType(post, mediaType);
        if (currentCount >= maxCount) {
            String mediaTypeName = mediaType == PostMedia.MediaType.IMAGE ? "images" : "videos";
            throw new IllegalArgumentException("Maximum " + maxCount + " " + mediaTypeName + " allowed per post");
        }

        // 파일 크기 검증
        if (mediaFile.getSize() > maxSize) {
            long maxSizeMB = maxSize / (1024 * 1024);
            throw new IllegalArgumentException("File size must be less than " + maxSizeMB + "MB");
        }

        try {
            // 원본 파일명에서 확장자 추출
            String originalFilename = mediaFile.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // 고유한 파일명 생성 (post-images/{postId}/{uuid}.{extension} 또는 post-videos/{postId}/{uuid}.{extension})
            String fileName = folder + "/" + postId + "/" + UUID.randomUUID() + extension;

            // S3에 업로드
            byte[] fileBytes = mediaFile.getBytes();
            String fileKey = s3Service.uploadBinaryFile(fileName, fileBytes, contentType);

            // S3 다운로드 URL 생성
            String mediaUrl = s3Service.generatePresignedDownloadUrl(fileKey);

            // 현재 게시물의 미디어 개수 확인 (순서 결정)
            Long mediaCount = postMediaRepository.countByPost(post);

            // PostMedia 엔티티 생성 및 저장
            PostMedia postMedia = PostMedia.builder()
                    .post(post)
                    .mediaType(mediaType)
                    .mediaUrl(mediaUrl)
                    .mediaKey(fileKey)
                    .mediaOrder(mediaCount.intValue())
                    .fileSize(mediaFile.getSize())
                    .contentType(contentType)
                    .build();

            postMediaRepository.save(postMedia);

            return PostMediaUploadResponseDto.builder()
                    .mediaId(postMedia.getId())
                    .mediaType(postMedia.getMediaType().name())
                    .mediaUrl(mediaUrl)
                    .mediaKey(fileKey)
                    .mediaOrder(postMedia.getMediaOrder())
                    .fileSize(postMedia.getFileSize())
                    .message("Post media uploaded successfully")
                    .build();

        } catch (IOException e) {
            throw new ExternalServiceException("Failed to upload post media: " + e.getMessage(), e);
        }
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private Post getPostById(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
    }

    private void validateAuthor(Post post, String username) {
        if (!post.getAuthor().getUsername().equals(username)) {
            throw new IllegalArgumentException("Not authorized to modify this post");
        }
    }

    private boolean isLiked(User user, Long postId) {
        return communityUtils.isPostLiked(user, postId);
    }

    private boolean isBookmarked(User user, Long postId) {
        return communityUtils.isPostBookmarked(user, postId);
    }
}
