package com.story.game.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for novel style learning from AI-IMAGE server
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelStyleLearnResponseDto {

    private String story_id;
    private String style_summary;
    private String atmosphere;
    private String visual_style;
    private String created_at;
    private String thumbnail_image_url;  // AI-IMAGE 서버가 생성한 썸네일 URL
}
