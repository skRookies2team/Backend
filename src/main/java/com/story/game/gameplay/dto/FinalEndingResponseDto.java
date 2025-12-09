package com.story.game.gameplay.dto;

import com.story.game.common.dto.FinalEndingDto;
import com.story.game.common.dto.GaugeDto;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * 최종 엔딩 전용 응답 DTO
 *
 * 게임 종료 후 엔딩 정보만 조회하는 API에서 사용
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinalEndingResponseDto {

    /**
     * 세션 ID
     */
    private String sessionId;

    /**
     * 게임 완료 여부
     */
    private Boolean isCompleted;

    /**
     * 최종 엔딩 정보
     */
    private FinalEndingDto finalEnding;

    /**
     * 최종 게이지 상태
     */
    private Map<String, Integer> finalGaugeStates;

    /**
     * 게이지 정의 (프론트엔드 표시용)
     */
    private List<GaugeDto> gaugeDefinitions;

    /**
     * 완료한 에피소드 수
     */
    private Integer completedEpisodesCount;
}
