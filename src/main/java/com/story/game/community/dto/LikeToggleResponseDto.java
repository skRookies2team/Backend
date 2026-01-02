package com.story.game.community.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "좋아요 토글 응답")
public class LikeToggleResponseDto {

    @Schema(description = "좋아요 상태 (true: 좋아요 추가됨, false: 좋아요 취소됨)", example = "true")
    private Boolean liked;

    @Schema(description = "응답 메시지", example = "좋아요가 추가되었습니다")
    private String message;

    @Schema(description = "사용자 이름", example = "user123")
    private String username;
}
