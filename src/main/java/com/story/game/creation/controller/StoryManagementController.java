package com.story.game.creation.controller;

import com.story.game.common.dto.EpisodeDto;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.StoryNodeDto;
import com.story.game.creation.dto.*;
import com.story.game.creation.service.SequentialGenerationService;
import com.story.game.creation.service.StoryEditingService;
import com.story.game.creation.service.StoryManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "Story Management", description = "스토리 생성 관리 API - 소설 업로드부터 스토리 생성까지의 전 과정을 단계별로 관리합니다")
public class StoryManagementController {

    private final StoryManagementService storyManagementService;
    private final StoryEditingService storyEditingService;
    private final SequentialGenerationService sequentialGenerationService;

    /**
     * 1. 소설 업로드 및 분석 시작
     */
    @PostMapping("/upload")
    @Operation(
            summary = "소설 업로드 및 분석 시작",
            description = "소설 텍스트를 업로드하고 AI 분석을 시작합니다. " +
                    "백그라운드에서 요약, 캐릭터, 게이지 추출이 진행됩니다."
    )
    public ResponseEntity<StoryUploadResponseDto> uploadNovel(
            @Valid @RequestBody StoryUploadRequestDto request) {
        log.info("=== Upload Novel Request ===");
        log.info("Title: {}", request.getTitle());

        StoryUploadResponseDto response = storyManagementService.uploadNovel(request);

        log.info("Novel uploaded. StoryId: {}", response.getStoryId());
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 요약 조회
     */
    @GetMapping("/{storyId}/summary")
    @Operation(
            summary = "요약 조회",
            description = "AI가 생성한 소설 요약을 조회합니다. " +
                    "status가 SUMMARY_READY 이상일 때 요약이 제공됩니다."
    )
    public ResponseEntity<StorySummaryResponseDto> getSummary(
            @PathVariable String storyId) {
        log.info("=== Get Summary Request ===");
        log.info("StoryId: {}", storyId);

        StorySummaryResponseDto response = storyManagementService.getSummary(storyId);

        return ResponseEntity.ok(response);
    }

    /**
     * 3. 캐릭터 조회
     */
    @GetMapping("/{storyId}/characters")
    @Operation(
            summary = "캐릭터 조회",
            description = "AI가 추출한 주요 캐릭터 목록을 조회합니다. " +
                    "각 캐릭터의 이름, 별칭, 설명, 관계 정보가 포함됩니다."
    )
    public ResponseEntity<StoryCharactersResponseDto> getCharacters(
            @PathVariable String storyId) {
        log.info("=== Get Characters Request ===");
        log.info("StoryId: {}", storyId);

        StoryCharactersResponseDto response = storyManagementService.getCharacters(storyId);

        return ResponseEntity.ok(response);
    }

    /**
     * 4. 게이지 제안 조회
     */
    @GetMapping("/{storyId}/gauges")
    @Operation(
            summary = "게이지 제안 조회",
            description = "AI가 소설 주제에 맞춰 제안한 5개의 게이지를 조회합니다. " +
                    "사용자는 이 중 2개를 선택해야 합니다."
    )
    public ResponseEntity<StoryGaugesResponseDto> getGauges(
            @PathVariable String storyId) {
        log.info("=== Get Gauges Request ===");
        log.info("StoryId: {}", storyId);

        StoryGaugesResponseDto response = storyManagementService.getGauges(storyId);

        return ResponseEntity.ok(response);
    }

    /**
     * 5. 게이지 선택
     */
    @PostMapping("/{storyId}/gauges/select")
    @Operation(
            summary = "게이지 선택",
            description = "제안된 5개 게이지 중 2개를 선택합니다. " +
                    "선택된 게이지는 게임 플레이 중 추적됩니다."
    )
    public ResponseEntity<GaugeSelectionResponseDto> selectGauges(
            @PathVariable String storyId,
            @Valid @RequestBody GaugeSelectionRequestDto request) {
        log.info("=== Select Gauges Request ===");
        log.info("StoryId: {}, Selected: {}", storyId, request.getSelectedGaugeIds());

        GaugeSelectionResponseDto response = storyManagementService.selectGauges(storyId, request);

        log.info("Gauges selected successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * 6. 생성 설정
     */
    @PostMapping("/{storyId}/config")
    @Operation(
            summary = "생성 설정",
            description = "스토리 생성을 위한 설정을 저장합니다. " +
                    "에피소드 수, 트리 깊이, 엔딩 타입 분포 등을 설정합니다."
    )
    public ResponseEntity<StoryConfigResponseDto> configureStory(
            @PathVariable String storyId,
            @Valid @RequestBody StoryConfigRequestDto request) {
        log.info("=== Configure Story Request ===");
        log.info("StoryId: {}", storyId);
        log.info("NumEpisodes: {}, MaxDepth: {}", request.getNumEpisodes(), request.getMaxDepth());

        StoryConfigResponseDto response = storyManagementService.configureStory(storyId, request);

        log.info("Story configured successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * 7. 스토리 생성 시작 (EP 1) - 동기 방식
     */
    @PostMapping("/{storyId}/generate")
    @Operation(
            summary = "스토리 생성 시작 (EP 1) - 동기 방식",
            description = "AI 서버에 에피소드 1 생성을 요청하고, 완료될 때까지 대기한 후 생성된 에피소드 데이터를 반환합니다."
    )
    public ResponseEntity<EpisodeDto> startGeneration(
            @PathVariable String storyId) {
        log.info("=== Start Generation Request (EP 1) - Synchronous ===");
        log.info("StoryId: {}", storyId);

        EpisodeDto response = sequentialGenerationService.startEpisodeGeneration(storyId);

        log.info("Story generation completed for episode 1. Title: {}", response.getTitle());
        return ResponseEntity.ok(response);
    }

    /**
     * 다음 에피소드 생성 시작 - 동기 방식
     */
    @PostMapping("/{storyId}/generate-next-episode")
    @Operation(
            summary = "다음 에피소드 생성 시작 - 동기 방식",
            description = "이전 에피소드에 이어 다음 에피소드의 생성을 시작하고, 완료될 때까지 대기한 후 생성된 에피소드 데이터를 반환합니다."
    )
    public ResponseEntity<EpisodeDto> generateNextEpisode(
            @PathVariable String storyId) {
        log.info("=== Generate Next Episode Request - Synchronous ===");
        log.info("StoryId: {}", storyId);

        EpisodeDto response = sequentialGenerationService.generateNextEpisode(storyId);

        log.info("Next episode generation completed. Title: {}", response.getTitle());
        return ResponseEntity.ok(response);
    }

    /**
     * 8a. 분석 진행률 조회
     */
    @GetMapping("/{storyId}/progress")
    @Operation(
            summary = "분석 진행률 조회",
            description = "초기 소설 분석 단계의 진행 상태를 조회합니다."
    )
    public ResponseEntity<StoryProgressResponseDto> getAnalysisProgress(
            @PathVariable String storyId) {
        log.debug("=== Get Analysis Progress Request ===");
        log.debug("StoryId: {}", storyId);

        StoryProgressResponseDto response = storyManagementService.getProgress(storyId);

        return ResponseEntity.ok(response);
    }

    /**
     * 9. 생성 완료 결과 조회
     */
    @GetMapping("/{storyId}/result")
    @Operation(
            summary = "생성 완료 결과 조회",
            description = "생성이 완료된 스토리의 정보를 조회합니다. " +
                    "storyDataId를 사용하여 게임을 시작할 수 있습니다."
    )
    public ResponseEntity<StoryResultResponseDto> getResult(
            @PathVariable String storyId) {
        log.info("=== Get Result Request ===");
        log.info("StoryId: {}", storyId);

        StoryResultResponseDto response = storyManagementService.getResult(storyId);

        return ResponseEntity.ok(response);
    }

    /**
     * 10. 전체 스토리 데이터 조회
     */
    @GetMapping("/{storyId}/data")
    @Operation(
            summary = "전체 스토리 데이터 조회",
            description = "생성 완료된 스토리의 전체 JSON 데이터를 조회합니다. " +
                    "프론트엔드에서 게임을 구성하기 위해 사용됩니다. " +
                    "에피소드, 노드, 선택지, 엔딩 등 모든 정보가 포함됩니다."
    )
    public ResponseEntity<FullStoryDto> getFullStoryData(
            @PathVariable String storyId) {
        log.info("=== Get Full Story Data Request ===");
        log.info("StoryId: {}", storyId);

        FullStoryDto response = storyManagementService.getFullStoryData(storyId);

        log.info("Full story data retrieved: {} episodes, {} nodes",
                response.getMetadata().getTotalEpisodes(),
                response.getMetadata().getTotalNodes());
        return ResponseEntity.ok(response);
    }

    /**
     * 11. S3에서 소설 읽어서 업로드 (S3 Pre-signed URL 방식)
     */
    @PostMapping("/upload-from-s3")
    @Operation(
            summary = "S3에서 소설 업로드",
            description = "S3에 업로드된 소설 파일을 읽어서 스토리 생성 프로세스를 시작합니다. " +
                    "프론트엔드는 먼저 /api/upload/presigned-url로 Pre-signed URL을 받아 S3에 파일을 업로드한 후, " +
                    "이 엔드포인트로 fileKey를 전달하여 분석을 시작합니다."
    )
    public ResponseEntity<StoryUploadResponseDto> uploadNovelFromS3(
            @Valid @RequestBody S3UploadRequestDto request) {
        log.info("=== Upload Novel From S3 Request ===");
        log.info("Title: {}, FileKey: {}", request.getTitle(), request.getFileKey());

        StoryUploadResponseDto response = storyManagementService.uploadNovelFromS3(request);

        log.info("Novel uploaded from S3. StoryId: {}", response.getStoryId());
        return ResponseEntity.ok(response);
    }

    /**
     * 노드 수정 및 하위 서브트리 재생성 (동기)
     */
    @PutMapping("/{storyId}/episodes/{episodeOrder}/nodes/{nodeId}/regenerate")
    @Operation(
            summary = "노드 수정 및 서브트리 재생성 (동기)",
            description = "특정 노드의 내용을 수정하고, 그 아래의 모든 하위 노드들을 AI가 자동으로 재생성합니다. " +
                    "동기 방식으로 처리되며, 완료될 때까지 대기한 후 재생성된 노드 목록을 반환합니다. " +
                    "Top-Down 방식으로 상위 노드 수정 시 하위 노드들이 새로운 내용에 맞춰 재생성됩니다."
    )
    public ResponseEntity<RegenerateSubtreeResponseDto> regenerateNodeSubtree(
            @PathVariable String storyId,
            @PathVariable Integer episodeOrder,
            @PathVariable String nodeId,
            @Valid @RequestBody UpdateNodeRequestDto request) {

        log.info("=== Regenerate Node Subtree Request (Sync) ===");
        log.info("Story ID: {}, Episode: {}, Node: {}", storyId, episodeOrder, nodeId);

        List<StoryNodeDto> regeneratedNodes = storyEditingService.regenerateSubtreeSync(
            storyId, nodeId, request
        );

        int totalNodes = regeneratedNodes != null ? regeneratedNodes.size() : 0;
        RegenerateSubtreeResponseDto response = RegenerateSubtreeResponseDto.builder()
                .status("success")
                .message("Subtree regenerated successfully")
                .regeneratedNodes(regeneratedNodes)
                .totalNodesRegenerated(totalNodes)
                .build();

        log.info("Subtree regeneration completed: {} nodes regenerated", totalNodes);
        return ResponseEntity.ok(response);
    }
}
