package com.story.game.gameplay.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartGameRequestDto {
    private Long storyDataId;
}
