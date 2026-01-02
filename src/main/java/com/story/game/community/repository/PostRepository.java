package com.story.game.community.repository;

import com.story.game.community.entity.Post;
import com.story.game.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    // N+1 최적화: 작성자 정보를 함께 로드
    @EntityGraph(attributePaths = {"author"})
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Page<Post> findByTypeOrderByCreatedAtDesc(Post.PostType type, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Page<Post> findByAuthorOrderByCreatedAtDesc(User author, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Page<Post> findByTitleContainingOrContentContaining(String title, String content, Pageable pageable);

    @EntityGraph(attributePaths = {"author"})
    Optional<Post> findById(Long id);

    long countByAuthor(User author);
}
