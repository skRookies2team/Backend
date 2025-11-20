package com.story.game.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartGameRequestDto {
    private Long storyDataId;
}
