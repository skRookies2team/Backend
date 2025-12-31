package com.story.game.achievement.entity;

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
    @Column(nullable = false, length = 50)
    private AchievementType type; // 업적 타입

    @Column(nullable = false)
    private Integer targetValue; // 달성 목표치

    @Column(length = 255)
    private String iconUrl; // 업적 아이콘 URL

    @Column(nullable = false)
    @Builder.Default
    private Integer points = 0; // 업적 포인트

    public enum AchievementType {
        PLAY_COUNT,         // 플레이 횟수
        COMPLETION_COUNT,   // 완료 횟수
        ENDING_COUNT,       // 엔딩 횟수
        CREATION_COUNT,     // 창작 횟수
        POST_COUNT          // 게시글 수
    }
}
