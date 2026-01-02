package com.story.game.community.repository;

import com.story.game.community.entity.Comment;
import com.story.game.community.entity.Post;
import com.story.game.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // N+1 query optimization: Fetch comments with author in one query
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post = :post AND c.parent IS NULL ORDER BY c.createdAt ASC")
    List<Comment> findTopLevelCommentsWithAuthor(@Param("post") Post post);

    // N+1 query optimization: Fetch all comments for a post with author
    @Query("SELECT c FROM Comment c JOIN FETCH c.author WHERE c.post = :post ORDER BY c.createdAt ASC")
    List<Comment> findAllCommentsWithAuthorByPost(@Param("post") Post post);
}
