package com.story.game.repository;

import com.story.game.entity.Like;
import com.story.game.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
    Optional<Like> findByUserAndTargetTypeAndTargetId(User user, Like.TargetType targetType, Long targetId);
    boolean existsByUserAndTargetTypeAndTargetId(User user, Like.TargetType targetType, Long targetId);
    void deleteByUserAndTargetTypeAndTargetId(User user, Like.TargetType targetType, Long targetId);
}
