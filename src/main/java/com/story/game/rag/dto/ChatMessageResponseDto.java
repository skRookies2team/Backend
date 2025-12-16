package com.story.game.rag.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageResponseDto {

    private String characterId;
    private String aiMessage;
    private List<RagSource> sources;
    private String timestamp;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RagSource {
        private String text;
        private Double score;
        private String sourceType;
    }
}
