package com.story.game.creation.controller;

import com.story.game.ai.dto.ImageGenerationResponseDto;
import com.story.game.creation.dto.NodeImageResponseDto;
import com.story.game.creation.dto.RegenerateImageRequestDto;
import com.story.game.creation.dto.UploadImageRequestDto;
import com.story.game.creation.dto.UploadImageResponseDto;
import com.story.game.creation.service.ImageCustomizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stories/{storyId}/images")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Image Customization", description = "스토리 노드 이미지 생성 및 커스터마이징 API")
public class ImageCustomizationController {

    private final ImageCustomizationService imageCustomizationService;

    @PostMapping("/nodes/{nodeId}/regenerate")
    @Operation(
        summary = "노드 이미지 생성",
        description = "커스텀 프롬프트를 사용하여 특정 스토리 노드의 이미지를 생성합니다. " +
                "이미지가 없는 노드에 새로 생성하거나, 기존 이미지를 새 프롬프트로 재생성할 수 있습니다."
    )
    public ResponseEntity<ImageGenerationResponseDto> generateNodeImage(
        @PathVariable String storyId,
        @PathVariable String nodeId,
        @RequestBody @Valid RegenerateImageRequestDto request
    ) {
        log.info("Generating image for node {} in story {}", nodeId, storyId);

        ImageGenerationResponseDto response = imageCustomizationService
            .generateNodeImage(storyId, nodeId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/nodes/{nodeId}/upload")
    @Operation(
        summary = "커스텀 이미지 업로드",
        description = "사용자가 직접 준비한 이미지를 특정 스토리 노드에 업로드합니다. " +
                "S3 Pre-signed URL을 반환하므로, 클라이언트는 이 URL로 이미지를 직접 업로드해야 합니다."
    )
    public ResponseEntity<UploadImageResponseDto> uploadCustomImage(
        @PathVariable String storyId,
        @PathVariable String nodeId,
        @RequestBody @Valid UploadImageRequestDto request
    ) {
        log.info("Uploading custom image for node {} in story {}", nodeId, storyId);

        UploadImageResponseDto response = imageCustomizationService
            .uploadCustomImage(storyId, nodeId, request);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/nodes/{nodeId}")
    @Operation(
        summary = "노드 이미지 조회",
        description = "특정 스토리 노드의 현재 이미지를 조회합니다. " +
                "이미지가 있는 경우 S3 Pre-signed Download URL을 반환합니다."
    )
    public ResponseEntity<NodeImageResponseDto> getNodeImage(
        @PathVariable String storyId,
        @PathVariable String nodeId
    ) {
        log.info("Getting image for node {} in story {}", nodeId, storyId);

        NodeImageResponseDto response = imageCustomizationService
            .getNodeImage(storyId, nodeId);

        return ResponseEntity.ok(response);
    }
}
