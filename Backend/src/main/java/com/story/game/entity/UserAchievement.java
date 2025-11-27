package com.story.game.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_achievements", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "achievement_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @Column(nullable = false)
    @Builder.Default
    private Integer currentValue = 0; // 현재 진행 상황

    @Column(nullable = false)
    @Builder.Default
    private Boolean isUnlocked = false; // 달성 여부

    @Column
    private LocalDateTime unlockedAt; // 달성 시간

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    // 진행도 업데이트
    public void updateProgress(Integer value) {
        this.currentValue = value;
        if (value >= achievement.getTargetValue() && !isUnlocked) {
            unlock();
        }
    }

    // 업적 달성
    public void unlock() {
        this.isUnlocked = true;
        this.unlockedAt = LocalDateTime.now();
    }
}
