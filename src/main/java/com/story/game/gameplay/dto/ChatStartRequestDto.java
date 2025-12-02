package com.story.game.gameplay.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatStartRequestDto {
    private String characterId;  // 스토리 내 캐릭터 ID
}
