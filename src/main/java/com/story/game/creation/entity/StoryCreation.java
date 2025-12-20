package com.story.game.creation.entity;

import com.story.game.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "story_creation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryCreation {

    @Id
    @Column(length = 50)
    private String id;  // story_123 형식

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;  // 스토리 생성자

    @Column(nullable = false)
    private String title;

    @Column(length = 100)
    private String genre;

    @Column(columnDefinition = "LONGTEXT")
    private String novelText;

    @Column(name = "s3_file_key", length = 500)
    private String s3FileKey;  // S3에 저장된 소설 파일의 키 (선택사항)

    @Column(name = "analysis_result_file_key", length = 500)
    private String analysisResultFileKey;  // S3에 저장된 분석 결과 파일의 키 (선택사항)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private CreationStatus status = CreationStatus.ANALYZING;

    // Analysis results
    @Column(columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "characters_json", columnDefinition = "TEXT")
    private String charactersJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gauges_json", columnDefinition = "TEXT")
    private String gaugesJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_gauge_ids", columnDefinition = "TEXT")
    private String selectedGaugeIdsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_characters_for_chat", columnDefinition = "TEXT")
    private String selectedCharactersForChatJson;

    // Configuration
    private String description;

    @Column(name = "num_episodes")
    private Integer numEpisodes;

    @Column(name = "max_depth")
    private Integer maxDepth;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ending_config", columnDefinition = "TEXT")
    private String endingConfigJson;

    @Column(name = "num_episode_endings")
    private Integer numEpisodeEndings;

    // Progress tracking
    @Column(name = "current_phase")
    private String currentPhase;

    @Column(name = "completed_episodes")
    private Integer completedEpisodes;

    @Column(name = "total_episodes_to_generate")
    private Integer totalEpisodesToGenerate;

    @Column(name = "progress_percentage")
    private Integer progressPercentage;

    @Column(name = "progress_message")
    private String progressMessage;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // Result
    @Column(name = "story_data_id")
    private Long storyDataId;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum CreationStatus {
        ANALYZING,           // 소설 분석 중
        SUMMARY_READY,       // 요약 완료
        CHARACTERS_READY,    // 캐릭터 추출 완료
        GAUGES_READY,        // 게이지 제안 완료
        GAUGES_SELECTED,     // 게이지 선택 완료
        CONFIGURED,          // 설정 완료
        GENERATING,          // 스토리 생성 중
        AWAITING_USER_ACTION, // 사용자 액션 대기 중 (에피소드 생성 후)
        COMPLETED,           // 생성 완료
        FAILED               // 생성 실패
    }
}
