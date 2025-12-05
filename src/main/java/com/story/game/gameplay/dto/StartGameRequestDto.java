package com.story.game.gameplay.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartGameRequestDto {
    @NotNull(message = "Story data ID is required")
    private Long storyDataId;
}
