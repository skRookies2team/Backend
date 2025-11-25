package com.story.game.community.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "post_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    @Column(nullable = false, length = 500)
    private String mediaUrl;

    @Column(nullable = false, length = 500)
    private String mediaKey;  // S3 파일 키

    @Column(nullable = false)
    @Builder.Default
    private Integer mediaOrder = 0;  // 미디어 정렬 순서

    @Column
    private Long fileSize;  // 파일 크기 (bytes)

    @Column(length = 100)
    private String contentType;  // MIME 타입

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum MediaType {
        IMAGE,   // 이미지
        VIDEO    // 동영상
    }
}
