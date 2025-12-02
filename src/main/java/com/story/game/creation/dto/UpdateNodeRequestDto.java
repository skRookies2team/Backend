package com.story.game.creation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 노드 수정 요청 DTO
 */
@Getter
@Builder
public class UpdateNodeRequestDto {
    private String nodeText;                    // 수정된 노드 텍스트
    private List<String> choices;               // 수정된 선택지
    private String situation;                   // 수정된 상황 설명
    private Map<String, String> npcEmotions;    // NPC 감정 상태
    private List<String> tags;                  // 태그
}
