package com.story.game.community.repository;

import com.story.game.community.entity.Post;
import com.story.game.community.entity.PostMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {
    List<PostMedia> findByPostOrderByMediaOrderAsc(Post post);
    Long countByPost(Post post);
    Long countByPostAndMediaType(Post post, PostMedia.MediaType mediaType);
}
