package com.story.game.gameplay.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatMessageRequestDto {
    private String characterId;
    private String userMessage;
}
