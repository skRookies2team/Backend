package com.story.game.gameplay.controller;

import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.entity.StoryData;
import com.story.game.gameplay.dto.ChoiceRequestDto;
import com.story.game.gameplay.dto.GameStateResponseDto;
import com.story.game.gameplay.dto.StartGameRequestDto;
import com.story.game.gameplay.service.GameService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "Game", description = "게임 플레이 및 스토리 관리 API")
public class GameController {



    private final GameService gameService;



    /**

     * Start a new game session

     */

    @PostMapping("/start")

    public ResponseEntity<GameStateResponseDto> startGame(

            @Valid @RequestBody StartGameRequestDto request,

            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("=== Start Game Request ===");

        log.info("StoryDataId: {}", request.getStoryDataId());

        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");

        com.story.game.auth.entity.User user = (com.story.game.auth.entity.User) userDetails;
        GameStateResponseDto response = gameService.startGame(request.getStoryDataId(), user);

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

        com.story.game.auth.entity.User user = (com.story.game.auth.entity.User) userDetails;
        GameStateResponseDto response = gameService.getGameState(sessionId, user);

        return ResponseEntity.ok(response);

    }



    /**

     * Make a choice and progress the story

     */

    @PostMapping("/{sessionId}/choice")

    public ResponseEntity<GameStateResponseDto> makeChoice(

            @PathVariable String sessionId,

            @Valid @RequestBody ChoiceRequestDto request,

            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("=== Make Choice Request ===");

        log.info("SessionId: {}", sessionId);

        log.info("ChoiceIndex: {}", request.getChoiceIndex());

        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");

        com.story.game.auth.entity.User user = (com.story.game.auth.entity.User) userDetails;
        GameStateResponseDto response = gameService.makeChoice(sessionId, request.getChoiceIndex(), user);

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

     * Get full story data by storyDataId (for frontend game composition)

     */

    @GetMapping("/stories/{storyDataId}/data")

    public ResponseEntity<FullStoryDto> getStoryData(

            @PathVariable Long storyDataId,

            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("=== Get Story Data Request ===");

        log.info("StoryDataId: {}", storyDataId);

        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");



        FullStoryDto response = gameService.getStoryDataById(storyDataId);



        log.info("Story data retrieved: {} episodes, {} nodes",

                response.getMetadata().getTotalEpisodes(),

                response.getMetadata().getTotalNodes());

        return ResponseEntity.ok(response);

    }

    /**
     * Get final ending information for completed game
     *
     * 게임 완료 후 최종 엔딩 정보만 조회하는 전용 API
     */
    @GetMapping("/{sessionId}/ending")
    public ResponseEntity<com.story.game.gameplay.dto.FinalEndingResponseDto> getFinalEnding(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("=== Get Final Ending Request ===");
        log.info("SessionId: {}", sessionId);
        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");

        com.story.game.auth.entity.User user = (com.story.game.auth.entity.User) userDetails;
        com.story.game.gameplay.dto.FinalEndingResponseDto response = gameService.getFinalEnding(sessionId, user);

        log.info("Final ending retrieved: {}", response.getFinalEnding() != null ? response.getFinalEnding().getTitle() : "null");

        return ResponseEntity.ok(response);
    }

}


