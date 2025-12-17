package com.story.game.rag.dto;

import lombok.*;

import java.util.Map;

/**
 * 게임 진행 상황 업데이트 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameProgressUpdateRequestDto {
    private String characterId;  // session_id로 사용
    private String content;  // 현재 노드 정보를 텍스트로 변환
    private Map<String, Object> metadata;  // 추가 메타데이터
}
