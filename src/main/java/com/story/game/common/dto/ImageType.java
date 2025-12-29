package com.story.game.common.dto;

/**
 * 이미지 타입 구분을 위한 Enum
 * 프론트엔드에서 이미지 용도를 명확히 식별할 수 있도록 함
 */
public enum ImageType {
    /**
     * 일반 장면 이미지 (게임 플레이 중 노드)
     */
    SCENE,

    /**
     * 에피소드 시작 이미지 (depth=0 노드)
     */
    EPISODE_START,

    /**
     * 에피소드 엔딩 이미지
     */
    EPISODE_ENDING,

    /**
     * 최종 게임 엔딩 이미지
     */
    FINAL_ENDING,

    /**
     * 스토리 썸네일/커버 이미지
     */
    THUMBNAIL
}
