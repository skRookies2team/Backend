package com.story.game.rag.service;

import com.story.game.auth.entity.User;
import com.story.game.auth.repository.UserRepository;
import com.story.game.rag.dto.*;
import com.story.game.rag.entity.ChatConversation;
import com.story.game.rag.entity.ChatMessage;
import com.story.game.rag.repository.ChatConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagService {

    private final WebClient relayServerWebClient;
    private final ChatConversationRepository chatConversationRepository;
    private final UserRepository userRepository;

    /**
     * 캐릭터를 RAG 시스템에 인덱싱
     * 실패 시 false 반환 (non-critical - 챗봇 기능은 부가 기능)
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
            log.warn("Failed to index character (non-critical): {}", request.getCharacterId(), e);
            // 캐릭터 인덱싱 실패는 치명적이지 않으므로 경고만 로그
            // 챗봇 기능은 작동하지 않지만 게임 진행은 가능
            return false;
        }
    }

    /**
     * 캐릭터 챗봇에게 메시지 전송 및 대화 내역 저장
     */
    @Transactional
    public ChatMessageResponseDto sendMessage(String username, ChatMessageRequestDto request) {
        log.info("=== Send Chat Message ===");
        log.info("Username: {}", username);
        log.info("Character: {}", request.getCharacterId());
        log.info("User message: {}", request.getUserMessage());

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // 대화 내역 조회 또는 생성
        ChatConversation conversation = chatConversationRepository
                .findByUserAndCharacterId(user, request.getCharacterId())
                .orElseGet(() -> {
                    ChatConversation newConv = ChatConversation.builder()
                            .user(user)
                            .characterId(request.getCharacterId())
                            .build();
                    return chatConversationRepository.save(newConv);
                });

        // 대화 내역을 request에 포함
        if (request.getConversationHistory() == null || request.getConversationHistory().isEmpty()) {
            List<ChatMessageRequestDto.ConversationMessage> history = conversation.getMessages().stream()
                    .map(msg -> ChatMessageRequestDto.ConversationMessage.builder()
                            .role(msg.getRole())
                            .content(msg.getContent())
                            .build())
                    .collect(Collectors.toList());
            request.setConversationHistory(history);
        }

        try {
            ChatMessageResponseDto response = relayServerWebClient.post()
                    .uri("/ai/chat/message")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ChatMessageResponseDto.class)
                    .block();

            if (response != null) {
                log.info("AI response: {}", response.getAiMessage());

                // 사용자 메시지 저장
                ChatMessage userMessage = ChatMessage.builder()
                        .role("user")
                        .content(request.getUserMessage())
                        .build();
                conversation.addMessage(userMessage);

                // AI 응답 저장
                ChatMessage assistantMessage = ChatMessage.builder()
                        .role("assistant")
                        .content(response.getAiMessage())
                        .build();
                conversation.addMessage(assistantMessage);

                chatConversationRepository.save(conversation);
            }

            return response;

        } catch (Exception e) {
            log.error("Failed to send chat message", e);
            throw new RuntimeException("Failed to send chat message: " + e.getMessage());
        }
    }

    /**
     * 특정 캐릭터와의 대화 내역 조회
     */
    @Transactional(readOnly = true)
    public ConversationHistoryResponseDto getConversationHistory(String username, String characterId) {
        log.info("=== Get Conversation History ===");
        log.info("Username: {}, Character: {}", username, characterId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        ChatConversation conversation = chatConversationRepository
                .findByUserAndCharacterId(user, characterId)
                .orElse(null);

        if (conversation == null) {
            return ConversationHistoryResponseDto.builder()
                    .characterId(characterId)
                    .messages(new ArrayList<>())
                    .build();
        }

        List<ConversationHistoryResponseDto.MessageDto> messages = conversation.getMessages().stream()
                .map(msg -> ConversationHistoryResponseDto.MessageDto.builder()
                        .messageId(msg.getId())
                        .role(msg.getRole())
                        .content(msg.getContent())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ConversationHistoryResponseDto.builder()
                .conversationId(conversation.getId())
                .characterId(conversation.getCharacterId())
                .characterName(conversation.getCharacterName())
                .storyId(conversation.getStoryId())
                .messages(messages)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    /**
     * 사용자의 모든 대화 목록 조회
     */
    @Transactional(readOnly = true)
    public List<ConversationHistoryResponseDto> getAllConversations(String username) {
        log.info("=== Get All Conversations ===");
        log.info("Username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<ChatConversation> conversations = chatConversationRepository.findByUserOrderByUpdatedAtDesc(user);

        return conversations.stream()
                .map(conv -> {
                    List<ConversationHistoryResponseDto.MessageDto> messages = conv.getMessages().stream()
                            .map(msg -> ConversationHistoryResponseDto.MessageDto.builder()
                                    .messageId(msg.getId())
                                    .role(msg.getRole())
                                    .content(msg.getContent())
                                    .createdAt(msg.getCreatedAt())
                                    .build())
                            .collect(Collectors.toList());

                    return ConversationHistoryResponseDto.builder()
                            .conversationId(conv.getId())
                            .characterId(conv.getCharacterId())
                            .characterName(conv.getCharacterName())
                            .storyId(conv.getStoryId())
                            .messages(messages)
                            .createdAt(conv.getCreatedAt())
                            .updatedAt(conv.getUpdatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * 게임 진행 상황을 NPC AI에 업데이트
     */
    public Boolean updateGameProgress(GameProgressUpdateRequestDto request) {
        log.info("=== Update Game Progress ===");
        log.info("Character: {}", request.getCharacterId());

        try {
            Boolean result = relayServerWebClient.post()
                    .uri("/ai/chat/update-progress")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            log.info("Game progress update result: {}", result);
            return result != null && result;

        } catch (Exception e) {
            log.warn("Failed to update game progress (non-critical): {}", e.getMessage());
            // 게임 진행 상황 업데이트 실패는 치명적이지 않으므로 경고만 로그
            return false;
        }
    }
}
