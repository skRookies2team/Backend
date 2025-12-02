package com.story.game.creation.dto;

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
public class RegenerateSubtreeResponseDto {
    private String status;
    private String message;
    private List<StoryNodeDto> regeneratedNodes;
    private int totalNodesRegenerated;
}