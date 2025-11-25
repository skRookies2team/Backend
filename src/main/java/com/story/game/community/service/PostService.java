package com.story.game.community.service;

import com.story.game.community.dto.CreatePostRequestDto;
import com.story.game.community.dto.PostResponseDto;
import com.story.game.community.entity.Bookmark;
import com.story.game.community.entity.Like;
import com.story.game.community.entity.Post;
import com.story.game.auth.entity.User;
import com.story.game.community.repository.BookmarkRepository;
import com.story.game.community.repository.LikeRepository;
import com.story.game.community.repository.PostRepository;
import com.story.game.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final BookmarkRepository bookmarkRepository;

    @Transactional
    public PostResponseDto createPost(String username, CreatePostRequestDto request) {
        User user = getUserByUsername(username);

        Post post = Post.builder()
                .author(user)
                .title(request.getTitle())
                .content(request.getContent())
                .type(request.getType())
                .build();

        postRepository.save(post);

        return PostResponseDto.from(post, false, false);
    }

    @Transactional
    public PostResponseDto updatePost(String username, Long postId, CreatePostRequestDto request) {
        Post post = getPostById(postId);
        validateAuthor(post, username);

        post.updatePost(request.getTitle(), request.getContent());
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

        User user = getUserByUsername(username);
        return PostResponseDto.from(post,
                isLiked(user, postId),
                isBookmarked(user, postId));
    }

    @Transactional(readOnly = true)
    public Page<PostResponseDto> getPosts(String username, Pageable pageable) {
        User user = getUserByUsername(username);
        return postRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(post -> PostResponseDto.from(post,
                        isLiked(user, post.getId()),
                        isBookmarked(user, post.getId())));
    }

    @Transactional(readOnly = true)
    public Page<PostResponseDto> getPostsByType(String username, Post.PostType type, Pageable pageable) {
        User user = getUserByUsername(username);
        return postRepository.findByTypeOrderByCreatedAtDesc(type, pageable)
                .map(post -> PostResponseDto.from(post,
                        isLiked(user, post.getId()),
                        isBookmarked(user, post.getId())));
    }

    @Transactional(readOnly = true)
    public Page<PostResponseDto> searchPosts(String username, String keyword, Pageable pageable) {
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
        return likeRepository.existsByUserAndTargetTypeAndTargetId(user, Like.TargetType.POST, postId);
    }

    private boolean isBookmarked(User user, Long postId) {
        return bookmarkRepository.existsByUserAndTargetTypeAndTargetId(user, Bookmark.TargetType.POST, postId);
    }
}
