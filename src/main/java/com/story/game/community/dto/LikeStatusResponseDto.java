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
@Schema(description = "좋아요 상태 조회 응답")
public class LikeStatusResponseDto {

    @Schema(description = "좋아요 상태 (true: 좋아요 누름, false: 안 누름)", example = "true")
    private Boolean liked;

    @Schema(description = "사용자 이름", example = "user123")
    private String username;
}
