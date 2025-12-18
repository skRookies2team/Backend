package com.story.game.rag.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationHistoryResponseDto {

    private Long conversationId;
    private String characterId;
    private String characterName;
    private String storyId;
    private List<MessageDto> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MessageDto {
        private Long messageId;
        private String role;  // "user" or "assistant"
        private String content;
        private LocalDateTime createdAt;
    }
}
