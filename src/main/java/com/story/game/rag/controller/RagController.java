package com.story.game.rag.controller;

import com.story.game.rag.dto.*;
import com.story.game.rag.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RAG", description = "캐릭터 챗봇 (RAG 기반)")
public class RagController {

    private final RagService ragService;

    @Operation(
            summary = "캐릭터 인덱싱",
            description = "캐릭터 정보를 RAG 시스템에 학습시킵니다. " +
                    "학습 후 해당 캐릭터와 채팅할 수 있습니다."
    )
    @PostMapping("/index-character")
    public ResponseEntity<Boolean> indexCharacter(
            @Valid @RequestBody CharacterIndexRequestDto request) {
        log.info("=== Index Character Request ===");
        log.info("Character: {} ({})", request.getName(), request.getCharacterId());

        Boolean result = ragService.indexCharacter(request);

        log.info("Character indexing completed: {}", result);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "캐릭터 챗봇 메시지 전송",
            description = "인덱싱된 캐릭터에게 메시지를 전송하고 AI 응답을 받습니다. " +
                    "RAG를 통해 캐릭터의 성격과 배경에 맞는 응답을 생성합니다. " +
                    "대화 내역은 자동으로 저장되며, 프론트엔드는 conversationHistory를 전달하지 않아도 됩니다."
    )
    @PostMapping("/chat")
    public ResponseEntity<ChatMessageResponseDto> sendMessage(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChatMessageRequestDto request) {
        log.info("=== Chat Message Request ===");
        log.info("Username: {}", userDetails.getUsername());
        log.info("Character: {}", request.getCharacterId());
        log.info("User message: {}", request.getUserMessage());

        ChatMessageResponseDto response = ragService.sendMessage(userDetails.getUsername(), request);

        log.info("AI response received");
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "특정 캐릭터와의 대화 내역 조회",
            description = "특정 캐릭터와 주고받은 모든 대화 내역을 조회합니다."
    )
    @GetMapping("/conversations/{characterId}")
    public ResponseEntity<ConversationHistoryResponseDto> getConversationHistory(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String characterId) {
        log.info("=== Get Conversation History ===");
        log.info("Username: {}, Character: {}", userDetails.getUsername(), characterId);

        ConversationHistoryResponseDto response = ragService.getConversationHistory(
                userDetails.getUsername(), characterId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "모든 대화 목록 조회",
            description = "사용자의 모든 캐릭터 대화 목록을 조회합니다."
    )
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationHistoryResponseDto>> getAllConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("=== Get All Conversations ===");
        log.info("Username: {}", userDetails.getUsername());

        List<ConversationHistoryResponseDto> response = ragService.getAllConversations(
                userDetails.getUsername());

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "특정 캐릭터와의 대화 내역 삭제",
            description = "게임 선택지 이동 시 특정 캐릭터와의 대화 내역을 삭제합니다."
    )
    @DeleteMapping("/conversations/{characterId}")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String characterId) {
        log.info("=== Delete Conversation ===");
        log.info("Username: {}, Character: {}", userDetails.getUsername(), characterId);

        ragService.deleteConversation(userDetails.getUsername(), characterId);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "모든 대화 내역 삭제",
            description = "사용자의 모든 캐릭터 대화 내역을 삭제합니다."
    )
    @DeleteMapping("/conversations")
    public ResponseEntity<Void> deleteAllConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("=== Delete All Conversations ===");
        log.info("Username: {}", userDetails.getUsername());

        ragService.deleteAllConversations(userDetails.getUsername());

        return ResponseEntity.noContent().build();
    }
}
