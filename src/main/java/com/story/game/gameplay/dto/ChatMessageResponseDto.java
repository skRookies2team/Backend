package com.story.game.gameplay.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatMessageResponseDto {
    private String characterId;
    private String characterName;
    private String aiMessage;
    private List<String> sources;  // RAG에서 참조한 소스 (선택적)
    private String timestamp;
}
