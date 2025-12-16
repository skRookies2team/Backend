package com.story.game.rag.service;

import com.story.game.rag.dto.CharacterIndexRequestDto;
import com.story.game.rag.dto.ChatMessageRequestDto;
import com.story.game.rag.dto.ChatMessageResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final WebClient relayServerWebClient;

    /**
     * 캐릭터를 RAG 시스템에 인덱싱
     */
    public Boolean indexCharacter(CharacterIndexRequestDto request) {
        log.info("=== Index Character ===");
        log.info("Character: {} ({})", request.getName(), request.getCharacterId());

        try {
            Boolean result = relayServerWebClient.post()
                    .uri("/ai/chat/index-character")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            log.info("Character indexing result: {}", result);
            return result != null && result;

        } catch (Exception e) {
            log.error("Failed to index character: {}", request.getCharacterId(), e);
            throw new RuntimeException("Failed to index character: " + e.getMessage());
        }
    }

    /**
     * 캐릭터 챗봇에게 메시지 전송
     */
    public ChatMessageResponseDto sendMessage(ChatMessageRequestDto request) {
        log.info("=== Send Chat Message ===");
        log.info("Character: {}", request.getCharacterId());
        log.info("User message: {}", request.getUserMessage());

        try {
            ChatMessageResponseDto response = relayServerWebClient.post()
                    .uri("/ai/chat/message")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatMessageResponseDto.class)
                    .block();

            if (response != null) {
                log.info("AI response: {}", response.getAiMessage());
            }

            return response;

        } catch (Exception e) {
            log.error("Failed to send chat message", e);
            throw new RuntimeException("Failed to send chat message: " + e.getMessage());
        }
    }
}
