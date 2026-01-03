package com.story.game.creation.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryConfigRequestDto {

    private String description;

    /**
     * 에피소드 수 (3~10)
     * null인 경우 소설 길이에 따라 자동 계산됨
     * - ~5만자: 3개
     * - 5~8만자: 4개
     * - 8~15만자: 5개
     * - 15~30만자: 6개
     * - 30~50만자: 7개
     * - 50~80만자: 8개
     * - 80~100만자: 9개
     * - 100만자 이상: 10개
     */
    @Min(3)
    @Max(10)
    private Integer numEpisodes;  // 기본값 제거: null이면 자동 계산

    @Min(2)
    @Max(5)
    @Builder.Default
    private Integer maxDepth = 3;

    @NotNull(message = "Ending configuration is required")
    private Map<String, Integer> endingConfig;

    @Min(1)
    @Max(5)
    @Builder.Default
    private Integer numEpisodeEndings = 3;
}
