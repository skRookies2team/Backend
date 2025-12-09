package com.story.game.story.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "episode_endings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EpisodeEnding {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * AI가 생성한 엔딩 ID (예: "ep1_ending_1", "ep2_ending_cooperative")
     * 로깅, 분석, 추적용
     */
    @Column(name = "ai_generated_id")
    private String aiGeneratedId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "episode_id", nullable = false)
    private Episode episode;

    @Column(nullable = false)
    private String title;

    @Column(name = "`condition`", columnDefinition = "TEXT")
    private String condition;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String text;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gauge_changes", columnDefinition = "TEXT")
    private String gaugeChanges;
}
