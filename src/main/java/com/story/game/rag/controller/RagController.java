package com.story.game.rag.controller;

import com.story.game.rag.dto.CharacterIndexRequestDto;
import com.story.game.rag.dto.ChatMessageRequestDto;
import com.story.game.rag.dto.ChatMessageResponseDto;
import com.story.game.rag.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
                    "RAG를 통해 캐릭터의 성격과 배경에 맞는 응답을 생성합니다."
    )
    @PostMapping("/chat")
    public ResponseEntity<ChatMessageResponseDto> sendMessage(
            @Valid @RequestBody ChatMessageRequestDto request) {
        log.info("=== Chat Message Request ===");
        log.info("Character: {}", request.getCharacterId());
        log.info("User message: {}", request.getUserMessage());

        ChatMessageResponseDto response = ragService.sendMessage(request);

        log.info("AI response received");
        return ResponseEntity.ok(response);
    }
}
