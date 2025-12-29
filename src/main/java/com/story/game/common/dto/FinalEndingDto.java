package com.story.game.common.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinalEndingDto {
    private String id;
    private String type;
    private String title;
    private String condition;
    private String summary;

    // Final ending image (최종 엔딩 이미지)
    private String imageUrl;
    private String imageFileKey;
}
