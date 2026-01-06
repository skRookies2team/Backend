package com.story.game.rag.service;

import com.story.game.auth.entity.User;
import com.story.game.auth.repository.UserRepository;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.rag.dto.*;
import com.story.game.rag.entity.ChatConversation;
import com.story.game.rag.entity.ChatMessage;
import com.story.game.rag.repository.ChatConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

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
    private final StoryCreationRepository storyCreationRepository;

    /**
     * 소설 원본을 RAG 시스템에 인덱싱
     * relay-server를 경유하여 RAG 서버에 전달
     * 실패 시 false 반환 (non-critical)
     */
    public Boolean indexNovel(NovelIndexRequestDto request) {
        log.info("=== Index Novel to RAG (via relay-server) ===");
        log.info("Story: {} ({})", request.getTitle(), request.getStoryId());
        log.info("File key: {}", request.getFileKey());

        try {
            Boolean result = relayServerWebClient.post()
                    .uri("/ai/chat/index-novel")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            log.info("Novel indexing result: {}", result);
            return result != null && result;

        } catch (WebClientResponseException e) {
            log.warn("RAG server returned error while indexing novel (non-critical): {} - Status: {}, Body: {}",
                    request.getStoryId(), e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (WebClientRequestException e) {
            log.warn("Failed to connect to RAG server while indexing novel (non-critical): {} - {}",
                    request.getStoryId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error while indexing novel to RAG (non-critical): {}", request.getStoryId(), e);
            return false;
        }
    }

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

        } catch (WebClientResponseException e) {
            log.warn("Relay server returned error while indexing character (non-critical): {} - Status: {}, Body: {}",
                    request.getCharacterId(), e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (WebClientRequestException e) {
            log.warn("Failed to connect to relay server while indexing character (non-critical): {} - {}",
                    request.getCharacterId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error while indexing character (non-critical): {}", request.getCharacterId(), e);
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
        log.info("Character ID (원본): {}", request.getCharacterId());
        log.info("User message: {}", request.getUserMessage());

        // characterId에서 storyId와 캐릭터 이름 추출 (story_39a5d3b1_로미오 → story_39a5d3b1, 로미오)
        String characterId = request.getCharacterId();
        String storyId = characterId;
        String extractedCharacterName = "캐릭터"; // 기본값

        if (characterId != null && characterId.startsWith("story_")) {
            String[] parts = characterId.split("_");
            if (parts.length >= 3) {
                // story_39a5d3b1_로미오 → story_39a5d3b1
                storyId = parts[0] + "_" + parts[1];
                // story_39a5d3b1_로미오 → 로미오
                extractedCharacterName = String.join("_", java.util.Arrays.copyOfRange(parts, 2, parts.length));
                log.info("CharacterId에서 추출: storyId={}, characterName={}", storyId, extractedCharacterName);
            }
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        // 람다에서 사용하기 위해 final 변수 생성
        final String finalStoryId = storyId;
        final String finalCharacterName = extractedCharacterName;

        // 대화 내역 조회 또는 생성
        ChatConversation conversation = chatConversationRepository
                .findByUserAndCharacterId(user, request.getCharacterId())
                .orElseGet(() -> {
                    ChatConversation newConv = ChatConversation.builder()
                            .user(user)
                            .characterId(request.getCharacterId())
                            .characterName(finalCharacterName)
                            .storyId(finalStoryId)
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

        // 캐릭터 이름을 request에 설정 (RAG 서버 연동을 위해 필수)
        // characterId에서 추출한 이름을 우선 사용 (기존 대화에 잘못 저장된 이름 무시)
        if (request.getCharacterName() == null || request.getCharacterName().isEmpty()) {
            request.setCharacterName(extractedCharacterName);
            log.info("CharacterId에서 추출한 캐릭터 이름 사용: {}", extractedCharacterName);
        }

        // StoryId를 별도 필드로 설정 (벡터 스토어 매칭용)
        request.setStoryId(storyId);
        log.info("Python RAG 서버로 전송: character_id={}, story_id={}, character_name={}",
                request.getCharacterId(), storyId, request.getCharacterName());

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

        } catch (WebClientResponseException e) {
            log.error("Relay server returned error while sending chat message - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Failed to send chat message: RAG server error - " + e.getStatusCode());
        } catch (WebClientRequestException e) {
            log.error("Failed to connect to relay server while sending chat message: {}", e.getMessage());
            throw new RuntimeException("Failed to send chat message: Cannot connect to RAG server");
        } catch (Exception e) {
            log.error("Unexpected error while sending chat message", e);
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
     * 특정 캐릭터와의 대화 내역 삭제
     */
    @Transactional
    public void deleteConversation(String username, String characterId) {
        log.info("=== Delete Conversation ===");
        log.info("Username: {}, Character: {}", username, characterId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        ChatConversation conversation = chatConversationRepository
                .findByUserAndCharacterId(user, characterId)
                .orElse(null);

        if (conversation != null) {
            chatConversationRepository.delete(conversation);
            log.info("Conversation deleted successfully: conversationId={}", conversation.getId());
        } else {
            log.info("No conversation found to delete");
        }
    }

    /**
     * 특정 스토리의 대화 내역 삭제
     */
    @Transactional
    public void deleteConversationsByStoryId(String username, String storyId) {
        log.info("=== Delete Conversations by Story ID ===");
        log.info("Username: {}, StoryId: {}", username, storyId);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<ChatConversation> conversations = chatConversationRepository
                .findByUserAndStoryIdOrderByUpdatedAtDesc(user, storyId);
        int count = conversations.size();

        chatConversationRepository.deleteAll(conversations);
        log.info("Deleted {} conversations for story: {}", count, storyId);
    }

    /**
     * 사용자의 모든 대화 내역 삭제
     */
    @Transactional
    public void deleteAllConversations(String username) {
        log.info("=== Delete All Conversations ===");
        log.info("Username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        List<ChatConversation> conversations = chatConversationRepository.findByUserOrderByUpdatedAtDesc(user);
        int count = conversations.size();

        chatConversationRepository.deleteAll(conversations);
        log.info("Deleted {} conversations", count);
    }

    /**
     * RAG 서버에서 스토리 관련 인덱싱 데이터 삭제
     * 소설 원본 및 캐릭터 인덱스를 모두 삭제
     * 실패해도 계속 진행 (non-critical)
     */
    public Boolean deleteStoryFromRag(String storyId) {
        log.info("=== Delete Story from RAG ===");
        log.info("StoryId: {}", storyId);

        try {
            Boolean result = relayServerWebClient.delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ai/chat/story/{storyId}")
                            .build(storyId))
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            log.info("RAG deletion result for story {}: {}", storyId, result);
            return result != null && result;

        } catch (WebClientResponseException e) {
            log.warn("RAG server returned error while deleting story (non-critical): {} - Status: {}, Body: {}",
                    storyId, e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (WebClientRequestException e) {
            log.warn("Failed to connect to RAG server while deleting story (non-critical): {} - {}",
                    storyId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error while deleting story from RAG (non-critical): {}", storyId, e);
            return false;
        }
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

        } catch (WebClientResponseException e) {
            log.warn("Relay server returned error while updating game progress (non-critical) - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (WebClientRequestException e) {
            log.warn("Failed to connect to relay server while updating game progress (non-critical): {}",
                    e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error while updating game progress (non-critical): {}", e.getMessage());
            return false;
        }
    }

    /**
     * 캐릭터 정보 설정 (학습 없이)
     * RAG 시스템의 캐릭터 페르소나/설명 업데이트
     */
    public Boolean setCharacter(CharacterSetRequestDto request) {
        log.info("=== Set Character ===");
        log.info("Character: {} ({})", request.getCharacterName(), request.getCharacterId());

        try {
            Boolean result = relayServerWebClient.post()
                    .uri("/ai/chat/set-character")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .block();

            log.info("Character set result: {}", result);
            return result != null && result;

        } catch (WebClientResponseException e) {
            log.warn("Relay server returned error while setting character (non-critical) - Status: {}, Body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (WebClientRequestException e) {
            log.warn("Failed to connect to relay server while setting character (non-critical): {}",
                    e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected error while setting character (non-critical): {}", e.getMessage());
            return false;
        }
    }
}
