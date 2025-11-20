package com.story.game.repository;

import com.story.game.entity.Comment;
import com.story.game.entity.Post;
import com.story.game.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostOrderByCreatedAtAsc(Post post);
    List<Comment> findByPostAndParentIsNullOrderByCreatedAtAsc(Post post);
    List<Comment> findByParentOrderByCreatedAtAsc(Comment parent);
    Page<Comment> findByAuthorOrderByCreatedAtDesc(User author, Pageable pageable);
    long countByPost(Post post);
    long countByAuthor(User author);
}
