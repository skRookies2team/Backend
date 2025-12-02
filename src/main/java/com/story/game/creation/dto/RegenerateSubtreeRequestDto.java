package com.story.game.creation.dto;

import com.story.game.common.dto.StoryNodeDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 서브트리 재생성 요청 DTO (Backend → Relay Server)
 */
@Getter
@Builder
public class RegenerateSubtreeRequestDto {
    private String episodeTitle;                // 에피소드 제목
    private Integer episodeOrder;               // 에피소드 순서
    private StoryNodeDto parentNode;            // 수정된 부모 노드
    private Integer currentDepth;               // 부모 노드의 depth
    private Integer maxDepth;                   // 최대 depth
    private String novelContext;                // 원작 소설 컨텍스트
    private List<String> previousChoices;       // 이전 선택 경로
    private List<String> selectedGaugeIds;      // 선택된 게이지 ID

    // 캐싱된 분석 결과 (성능 최적화)
    private String summary;                     // 소설 요약 (캐시)
    private String charactersJson;              // 캐릭터 정보 (캐시)
    private String gaugesJson;                  // 게이지 정보 (캐시)
}
