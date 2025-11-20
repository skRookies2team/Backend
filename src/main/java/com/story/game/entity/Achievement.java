package com.story.game.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "achievements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code; // 업적 고유 코드 (예: FIRST_STORY, ALL_ENDINGS)

    @Column(nullable = false, length = 100)
    private String name; // 업적 이름

    @Column(length = 500)
    private String description; // 업적 설명

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AchievementType type; // 업적 타입

    @Column(nullable = false)
    private Integer targetValue; // 달성 목표치

    @Column(length = 255)
    private String iconUrl; // 업적 아이콘 URL

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 0; // 업적 포인트

    public enum AchievementType {
        STORY_COMPLETE,     // 스토리 완료
        ENDING_UNLOCK,      // 엔딩 달성
        PLAY_COUNT,         // 플레이 횟수
        COMMUNITY_ACTIVE,   // 커뮤니티 활동
        SPECIAL             // 특별 업적
    }
}
