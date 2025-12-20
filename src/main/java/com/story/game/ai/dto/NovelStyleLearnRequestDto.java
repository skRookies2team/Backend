package com.story.game.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for learning novel style in AI-IMAGE server
 * Maps to relay-server's /ai/learn-novel-style endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelStyleLearnRequestDto {

    @NotBlank(message = "Story ID is required")
    private String story_id;

    @NotBlank(message = "Novel text is required")
    private String novel_text;

    private String title;
}
