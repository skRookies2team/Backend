package com.story.game.gameplay.entity;

import com.story.game.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Entity
@Table(name = "game_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "story_data_id", nullable = false)
    private Long storyDataId;

    @Column(name = "current_episode_id")
    private String currentEpisodeId;

    @Column(name = "current_node_id")
    private String currentNodeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gauge_states", columnDefinition = "json")
    @Builder.Default
    private Map<String, Integer> gaugeStates = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accumulated_tags", columnDefinition = "json")
    @Builder.Default
    private Map<String, Integer> accumulatedTags = new HashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "visited_nodes", columnDefinition = "json")
    @Builder.Default
    private List<String> visitedNodes = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_episodes", columnDefinition = "json")
    @Builder.Default
    private List<String> completedEpisodes = new ArrayList<>();

    @Column(name = "is_completed")
    @Builder.Default
    private Boolean isCompleted = false;

    @Column(name = "final_ending_id")
    private String finalEndingId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
