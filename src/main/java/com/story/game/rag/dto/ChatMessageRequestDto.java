package com.story.game.rag.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRequestDto {

    @NotBlank(message = "Character ID is required")
    @Size(max = 100)
    private String characterId;

    @Size(max = 100)
    private String characterName;  // 캐릭터 이름 (RAG 서버로 전달)

    @NotBlank(message = "User message is required")
    @Size(max = 2000)
    private String userMessage;

    @Valid
    private List<ConversationMessage> conversationHistory;

    @Min(1)
    @Max(4000)
    private Integer maxTokens;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConversationMessage {
        @NotBlank
        @Size(max = 20)
        private String role;  // "user" or "assistant"

        @NotBlank
        @Size(max = 2000)
        private String content;
    }
}