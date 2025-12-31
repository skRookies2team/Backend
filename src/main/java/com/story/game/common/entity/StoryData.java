package com.story.game.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "story_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 100)
    private String genre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "story_file_key", nullable = false)
    private String storyFileKey;

    @Column(name = "total_episodes")
    private Integer totalEpisodes;

    @Column(name = "total_nodes")
    private Integer totalNodes;

    // Story thumbnail/cover image
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "thumbnail_file_key", length = 500)
    private String thumbnailFileKey;

    // Stats
    @Column(name = "likes_count")
    @Builder.Default
    private Long likesCount = 0L;

    @Column(name = "view_count")
    @Builder.Default
    private Long viewCount = 0L;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 조회수 증가
     */
    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0L : this.viewCount) + 1;
    }

    /**
     * 좋아요 수 증가
     */
    public void incrementLikesCount() {
        this.likesCount = (this.likesCount == null ? 0L : this.likesCount) + 1;
    }

    /**
     * 좋아요 수 감소
     */
    public void decrementLikesCount() {
        this.likesCount = (this.likesCount == null ? 0L : this.likesCount) - 1;
        if (this.likesCount < 0) {
            this.likesCount = 0L;
        }
    }
}
