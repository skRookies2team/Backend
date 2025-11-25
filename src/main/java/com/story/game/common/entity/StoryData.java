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

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "story_json", columnDefinition = "LONGTEXT", nullable = false)
    private String storyJson;

    @Column(name = "total_episodes")
    private Integer totalEpisodes;

    @Column(name = "total_nodes")
    private Integer totalNodes;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
