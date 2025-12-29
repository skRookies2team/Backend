package com.story.game.creation.controller;

import com.story.game.ai.dto.ImageGenerationResponseDto;
import com.story.game.creation.dto.NodeImageResponseDto;
import com.story.game.creation.dto.RegenerateImageRequestDto;
import com.story.game.creation.dto.UploadImageRequestDto;
import com.story.game.creation.dto.UploadImageResponseDto;
import com.story.game.creation.service.ImageCustomizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stories/{storyId}/images")
@RequiredArgsConstructor
@Slf4j
public class ImageCustomizationController {

    private final ImageCustomizationService imageCustomizationService;

    @PostMapping("/nodes/{nodeId}/regenerate")
    public ResponseEntity<ImageGenerationResponseDto> regenerateNodeImage(
        @PathVariable String storyId,
        @PathVariable String nodeId,
        @RequestBody @Valid RegenerateImageRequestDto request
    ) {
        log.info("Regenerating image for node {} in story {}", nodeId, storyId);

        ImageGenerationResponseDto response = imageCustomizationService
            .regenerateImage(storyId, nodeId, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/nodes/{nodeId}/upload")
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
