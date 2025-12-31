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
}
