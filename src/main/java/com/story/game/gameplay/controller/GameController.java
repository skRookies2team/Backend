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

        com.story.game.auth.entity.User user = convertToUser(userDetails);
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

        com.story.game.auth.entity.User user = convertToUser(userDetails);
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

        com.story.game.auth.entity.User user = convertToUser(userDetails);
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
    @io.swagger.v3.oas.annotations.Operation(
            summary = "최종 엔딩 조회",
            description = "게임 완료 후 최종 엔딩 정보를 조회합니다. 게임이 완료되지 않았을 경우 에러를 반환합니다."
    )
    public ResponseEntity<com.story.game.gameplay.dto.FinalEndingResponseDto> getFinalEnding(
            @PathVariable String sessionId,
            @AuthenticationPrincipal UserDetails userDetails) {

        log.info("=== Get Final Ending Request ===");
        log.info("SessionId: {}", sessionId);
        log.info("User: {}", userDetails != null ? userDetails.getUsername() : "anonymous");

        com.story.game.auth.entity.User user = convertToUser(userDetails);
        com.story.game.gameplay.dto.FinalEndingResponseDto response = gameService.getFinalEnding(sessionId, user);

        log.info("Final ending retrieved: {}", response.getFinalEnding() != null ? response.getFinalEnding().getTitle() : "null");

        return ResponseEntity.ok(response);
    }

    /**
     * Get selected characters for NPC chat by storyDataId
     * 프론트엔드가 StoryData ID로 선택된 캐릭터를 조회할 수 있도록 지원
     */
    @GetMapping("/stories/{storyDataId}/selected-characters")
    @io.swagger.v3.oas.annotations.Operation(
            summary = "선택된 NPC 캐릭터 조회 (StoryDataId 기반)",
            description = "StoryData ID로 선택된 NPC 캐릭터 목록을 조회합니다. 게임 플레이 중 대화 가능한 NPC를 확인할 수 있습니다."
    )
    public ResponseEntity<com.story.game.creation.dto.SelectedCharactersResponseDto> getSelectedCharactersByStoryDataId(
            @PathVariable Long storyDataId) {

        log.info("=== Get Selected Characters by StoryDataId ===");
        log.info("StoryDataId: {}", storyDataId);

        com.story.game.creation.dto.SelectedCharactersResponseDto response =
                gameService.getSelectedCharactersByStoryDataId(storyDataId);

        log.info("Selected characters retrieved: hasSelection={}, count={}",
                response.isHasSelection(),
                response.getSelectedCharacters() != null ? response.getSelectedCharacters().size() : 0);

        return ResponseEntity.ok(response);
    }

    /**
     * 안전한 User 형변환 Helper 메서드
     * UserDetails를 User로 안전하게 형변환
     */
    private com.story.game.auth.entity.User convertToUser(UserDetails userDetails) {
        if (userDetails == null) {
            throw new IllegalStateException("User authentication required");
        }

        if (!(userDetails instanceof com.story.game.auth.entity.User)) {
            throw new ClassCastException("Invalid user type: " + userDetails.getClass().getName());
        }

        return (com.story.game.auth.entity.User) userDetails;
    }

}


