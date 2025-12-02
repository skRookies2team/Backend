package com.story.game.creation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.story.game.common.dto.StoryNodeDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegenerateProgressDto {
    private String taskId;
    private String status; // e.g., "pending", "in_progress", "completed", "failed"
    private String message;
    private int progress;
    private List<StoryNodeDto> regeneratedNodes;
}