package com.story.game.common.dto;

import lombok.*;

/**
 * 노드 이미지 정보를 담는 DTO
 * 이미지 URL과 함께 타입 정보를 제공하여 프론트엔드에서 용도별로 처리 가능
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeImageInfo {
    /**
     * 이미지 URL (S3 presigned URL 또는 public URL)
     */
    private String imageUrl;

    /**
     * 이미지 타입 (SCENE, EPISODE_START, EPISODE_ENDING, FINAL_ENDING, THUMBNAIL)
     */
    private ImageType type;

    /**
     * S3 파일 키 (선택사항, 디버깅/관리용)
     */
    private String fileKey;

    /**
     * 이미지 설명 (접근성/대체 텍스트용)
     */
    private String altText;

    /**
     * 간편 생성자: URL과 타입만으로 생성
     */
    public static NodeImageInfo of(String imageUrl, ImageType type) {
        return NodeImageInfo.builder()
                .imageUrl(imageUrl)
                .type(type)
                .build();
    }

    /**
     * 간편 생성자: URL, 타입, 설명으로 생성
     */
    public static NodeImageInfo of(String imageUrl, ImageType type, String altText) {
        return NodeImageInfo.builder()
                .imageUrl(imageUrl)
                .type(type)
                .altText(altText)
                .build();
    }
}
