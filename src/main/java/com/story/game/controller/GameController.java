package com.story.game.controller;

import com.story.game.dto.*;
import com.story.game.entity.StoryData;
import com.story.game.service.GameService;
import com.story.game.service.StoryGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class GameController {

    private final GameService gameService;
    private final StoryGenerationService storyGenerationService;

    /**
     * Start a new game session
     */
    @PostMapping("/start")
    public ResponseEntity<GameStateResponseDto> startGame(
            @RequestBody StartGameRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("=== Start Game Request ===");
        log.info("StoryDataId: {}", request.getStoryDataId());
        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");
        GameStateResponseDto response = gameService.startGame(request.getStoryDataId());
        log.info("Game started. SessionId: {}", response.getSessionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Get current game state
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<GameStateResponseDto> getGameState(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("=== Get Game State Request ===");
        log.info("SessionId: {}", sessionId);
        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");
        GameStateResponseDto response = gameService.getGameState(sessionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Make a choice and progress the story
     */
    @PostMapping("/{sessionId}/choice")
    public ResponseEntity<GameStateResponseDto> makeChoice(
            @PathVariable String sessionId,
            @RequestBody ChoiceRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        log.info("=== Make Choice Request ===");
        log.info("SessionId: {}", sessionId);
        log.info("ChoiceIndex: {}", request.getChoiceIndex());
        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");
        GameStateResponseDto response = gameService.makeChoice(sessionId, request.getChoiceIndex());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all available stories
     */
    @GetMapping("/stories")
    public ResponseEntity<List<StoryData>> getAllStories() {
        List<StoryData> stories = gameService.getAllStories();
        return ResponseEntity.ok(stories);
    }

    /**
     * Upload a new story (JSON from Python AI server)
     */
    @PostMapping("/stories")
    public ResponseEntity<StoryData> uploadStory(
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestBody String storyJson) {
        StoryData storyData = gameService.saveStoryData(title, description, storyJson);
        return ResponseEntity.ok(storyData);
    }

    /**
     * Generate a new story using Python AI server
     */
    @PostMapping("/stories/generate")
    public ResponseEntity<StoryData> generateStory(@Valid @RequestBody GenerateStoryRequestDto request) {
        StoryData storyData = storyGenerationService.generateStory(request);
        return ResponseEntity.ok(storyData);
    }

    /**
     * Check AI server health status
     */
    @GetMapping("/ai/health")
    public ResponseEntity<Map<String, Object>> checkAiHealth() {
        boolean isHealthy = storyGenerationService.checkAiServerHealth();
        return ResponseEntity.ok(Map.of(
                "status", isHealthy ? "healthy" : "unhealthy",
                "aiServer", isHealthy
        ));
    }
}
