package com.story.game.gameplay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.FinalEndingDto;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.GaugeDto;
import com.story.game.common.dto.ImageType;
import com.story.game.common.dto.NodeImageInfo;
import com.story.game.common.dto.StoryChoiceDto;
import com.story.game.common.dto.EpisodeDto;
import com.story.game.common.dto.StoryNodeDto;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.creation.repository.StoryCreationRepository;
import com.story.game.gameplay.dto.GameStateResponseDto;
import com.story.game.gameplay.entity.GameSession;
import com.story.game.gameplay.repository.GameSessionRepository;
import com.story.game.rag.dto.GameProgressUpdateRequestDto;
import com.story.game.rag.service.RagService;
import com.story.game.achievement.service.AchievementService;
import com.story.game.story.entity.Episode;
import com.story.game.story.entity.EpisodeEnding;
import com.story.game.story.entity.StoryChoice;
import com.story.game.story.entity.StoryNode;
import com.story.game.story.mapper.StoryMapper;
import com.story.game.story.repository.EpisodeEndingRepository;
import com.story.game.story.repository.EpisodeRepository;
import com.story.game.story.repository.StoryChoiceRepository;
import com.story.game.story.repository.StoryNodeRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

// [SpEL] 조건 계산을 위한 패키지 임포트
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final GameSessionRepository gameSessionRepository;
    private final StoryDataRepository storyDataRepository;
    private final ObjectMapper objectMapper;
    private final com.story.game.ai.service.RelayServerClient relayServerClient;
    private final EpisodeRepository episodeRepository;
    private final StoryNodeRepository storyNodeRepository;
    private final StoryChoiceRepository storyChoiceRepository;
    private final StoryCreationRepository storyCreationRepository;
    private final EpisodeEndingRepository episodeEndingRepository;
    private final StoryMapper storyMapper;
    private final com.story.game.infrastructure.s3.S3Service s3Service;

    @Value("${aws.s3.bucket}")
    private String s3BucketName;

    // [SpEL] 파서 인스턴스 생성
    private final ExpressionParser parser = new SpelExpressionParser();
    private final RagService ragService;
    private final BgmService bgmService;
    private final AchievementService achievementService;

    @Transactional
    public GameStateResponseDto startGame(Long storyDataId, com.story.game.auth.entity.User user) {
        StoryData storyData = storyDataRepository.findById(storyDataId)
                .orElseThrow(() -> new RuntimeException("Story not found: " + storyDataId));
        StoryCreation storyCreation = storyCreationRepository.findByStoryDataId(storyData.getId())
                .orElseThrow(() -> new RuntimeException("StoryCreation not found for StoryData: " + storyData.getId()));

        // 캐릭터 선택 필수 검증
        if (storyCreation.getSelectedCharactersForChatJson() == null ||
            storyCreation.getSelectedCharactersForChatJson().isBlank()) {
            throw new IllegalStateException("Characters must be selected to start the game. NPC chatbot requires character selection.");
        }

        // Extract character ID for RAG integration
        String characterId = extractCharacterIdFromJson(storyCreation.getSelectedCharactersForChatJson());

        Episode firstEpisode = episodeRepository.findByStoryAndOrder(storyCreation, 1)
                .orElseThrow(() -> new RuntimeException("First episode not found"));

        StoryNode rootNode = storyNodeRepository.findByEpisodeAndDepth(firstEpisode, 0)
                .orElseThrow(() -> new RuntimeException("Root node not found"));

        Map<String, Integer> initialGauges = new HashMap<>();
        try {
            List<String> selectedGaugeIds = new ArrayList<>();
            if (storyCreation.getSelectedGaugeIdsJson() != null) {
                selectedGaugeIds = objectMapper.readValue(storyCreation.getSelectedGaugeIdsJson(),
                        new TypeReference<List<String>>() {});
            }

            if (!selectedGaugeIds.isEmpty()) {
                for (String gaugeId : selectedGaugeIds) {
                    initialGauges.put(gaugeId, 50);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse selected gauges JSON", e);
        }

        GameSession session = GameSession.builder()
                .user(user)
                .storyDataId(storyDataId)
                .storyCreationId(storyCreation.getId())
                .selectedCharacterId(characterId)
                .currentEpisodeId(firstEpisode.getId().toString())
                .currentNodeId(rootNode.getId().toString())
                .gaugeStates(initialGauges)
                .accumulatedTags(new HashMap<>())
                .visitedNodes(new ArrayList<>(List.of(rootNode.getId().toString())))
                .completedEpisodes(new ArrayList<>())
                .build();

        session = gameSessionRepository.save(session);
        log.info("Game session created for user: {} with sessionId: {}", user.getUsername(), session.getId());

        NodeImageInfo nodeImage = generateNodeImage(
                session.getStoryCreationId(),
                rootNode.getId().toString(),
                storyMapper.toStoryNodeDto(rootNode),
                storyMapper.toEpisodeDto(firstEpisode)
        );

        // Get BGM for first episode (episode-based, not node-based)
        com.story.game.gameplay.dto.BgmDto bgm = null;
        try {
            bgm = bgmService.getBgmForEpisode(
                storyDataId,
                firstEpisode.getId(),
                firstEpisode.getTitle(),
                firstEpisode.getIntroText()
            );

            // Save BGM to session for this episode
            if (bgm != null) {
                session.setCurrentEpisodeBgmJson(objectMapper.writeValueAsString(bgm));
                gameSessionRepository.save(session);
                log.info("BGM set for episode {}: mood={}", firstEpisode.getId(), bgm.getMood());
            }
        } catch (Exception e) {
            log.warn("Failed to get BGM for first episode: {}", e.getMessage());
        }

        return buildGameStateResponse(session, storyCreation, firstEpisode, rootNode, true, nodeImage, bgm);
    }

    @Transactional(readOnly = true)
    public GameStateResponseDto getGameState(String sessionId, com.story.game.auth.entity.User user) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
            throw new com.story.game.common.exception.UnauthorizedException("You don't have permission to access this game session");
        }

        StoryCreation storyCreation = storyCreationRepository.findByStoryDataId(session.getStoryDataId())
                .orElseThrow(() -> new RuntimeException("StoryCreation not found"));

        if (Boolean.TRUE.equals(session.getIsCompleted())) {
            EpisodeEnding lastEpisodeEnding = null;
            FinalEndingDto matchedFinalEnding = null;

            if (session.getFinalEndingId() != null) {
                matchedFinalEnding = findFinalEndingById(storyCreation, session.getFinalEndingId());
            }

            return handleGameEndResponse(session, storyCreation, lastEpisodeEnding, matchedFinalEnding);
        }

        Episode currentEpisode = episodeRepository.findById(UUID.fromString(session.getCurrentEpisodeId()))
                .orElseThrow(() -> new RuntimeException("Episode not found"));
        StoryNode currentNode = storyNodeRepository.findById(UUID.fromString(session.getCurrentNodeId()))
                .orElseThrow(() -> new RuntimeException("Node not found"));

        boolean isFirstNodeOfEpisode = currentNode.getDepth() == 0;

        NodeImageInfo nodeImage = generateNodeImage(
                session.getStoryCreationId(),
                session.getCurrentNodeId(),
                storyMapper.toStoryNodeDto(currentNode),
                storyMapper.toEpisodeDto(currentEpisode)
        );

        // Get BGM for current episode (from session storage)
        com.story.game.gameplay.dto.BgmDto bgm = null;
        try {
            if (session.getCurrentEpisodeBgmJson() != null) {
                bgm = objectMapper.readValue(session.getCurrentEpisodeBgmJson(), com.story.game.gameplay.dto.BgmDto.class);
                log.debug("Using stored BGM for episode {}: mood={}", currentEpisode.getId(), bgm.getMood());
            } else {
                log.warn("No BGM stored for current episode {}", currentEpisode.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve BGM for current episode: {}", e.getMessage());
        }

        return buildGameStateResponse(session, storyCreation, currentEpisode, currentNode, isFirstNodeOfEpisode, nodeImage, bgm);
    }

    @Transactional
    public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex, com.story.game.auth.entity.User user) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
            throw new com.story.game.common.exception.UnauthorizedException("You don't have permission to modify this game session");
        }

        if (Boolean.TRUE.equals(session.getIsCompleted())) {
            return getGameState(sessionId, user);
        }

        StoryNode currentNode = storyNodeRepository.findById(UUID.fromString(session.getCurrentNodeId()))
                .orElseThrow(() -> new RuntimeException("Current node not found: " + session.getCurrentNodeId()));

        List<StoryChoice> choices = storyChoiceRepository.findBySourceNodeOrderByChoiceOrderAsc(currentNode);

        if (choices.isEmpty()) {
            return handleEpisodeEnd(session);
        }

        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            throw new com.story.game.common.exception.InvalidStateException("Invalid choice index: " + choiceIndex);
        }

        StoryChoice selectedChoice = choices.get(choiceIndex);

        if (selectedChoice.getTags() != null) {
            try {
                List<String> tags = objectMapper.readValue(selectedChoice.getTags(), new TypeReference<List<String>>() {});
                for (String tag : tags) {
                    session.getAccumulatedTags().merge(tag, 1, Integer::sum);
                }
            } catch (Exception e) {
                log.error("Failed to parse choice tags", e);
            }
        }

        StoryNode nextNode = selectedChoice.getDestinationNode();

        if (nextNode != null) {
            session.setCurrentNodeId(nextNode.getId().toString());
            session.getVisitedNodes().add(nextNode.getId().toString());

            // 다음 선택지로 넘어갈 때 현재 스토리의 대화 내역만 삭제
            try {
                String storyId = session.getStoryCreationId();
                ragService.deleteConversationsByStoryId(user.getUsername(), storyId);
                log.info("Deleted conversations for user: {} and story: {} on choice selection",
                        user.getUsername(), storyId);
            } catch (Exception e) {
                log.warn("Failed to delete conversations (non-critical): {}", e.getMessage());
            }

            if (nextNode.getNodeType() != null) {
                String nodeType = nextNode.getNodeType().toUpperCase();
                if (nodeType.equals("ENDING")) {
                    gameSessionRepository.save(session);
                    return handleEpisodeEnd(session);
                } else if (nodeType.equals("FINAL_ENDING")) {
                    return handleGameEnd(session, nextNode.getEpisode().getStory(), null);
                }
            }

            gameSessionRepository.save(session);


            // NPC AI에 게임 진행 상황 업데이트 (비동기, 실패해도 게임 진행에 영향 없음)
            try {
                // StoryDataId null 체크
                if (session.getStoryCreationId() == null) {
                    log.warn("StoryCreationId is null, skipping NPC AI update for session: {}", session.getId());
                } else {
                    String progressContent = buildProgressContent(selectedChoice, currentNode, nextNode);

                    GameProgressUpdateRequestDto updateRequest = GameProgressUpdateRequestDto.builder()
                            .characterId(session.getStoryCreationId())  // StoryCreation ID를 session_id로 사용
                            .content(progressContent)
                            .metadata(Map.of(
                                    "nodeId", nextNode.getId().toString(),
                                    "depth", nextNode.getDepth(),
                                    "episodeId", nextNode.getEpisode().getId().toString(),
                                    "gameSessionId", session.getId(),  // 게임 세션 ID (참고용)
                                    "timestamp", System.currentTimeMillis()
                            ))
                            .build();

                    ragService.updateGameProgress(updateRequest);
                }
            } catch (Exception e) {
                log.warn("Failed to update game progress to NPC AI (non-critical): {}", e.getMessage());
            }


            List<StoryChoice> nextNodeChoices = storyChoiceRepository.findBySourceNodeOrderByChoiceOrderAsc(nextNode);
            if (nextNodeChoices.isEmpty()) {
                return handleEpisodeEnd(session);
            }

            NodeImageInfo nodeImage = generateNodeImage(
                    session.getStoryCreationId(),
                    nextNode.getId().toString(),
                    storyMapper.toStoryNodeDto(nextNode),
                    storyMapper.toEpisodeDto(currentNode.getEpisode())
            );

            // Get BGM for current episode (from session storage)
            // Since we're in the same episode, use the stored BGM
            com.story.game.gameplay.dto.BgmDto bgm = null;
            try {
                if (session.getCurrentEpisodeBgmJson() != null) {
                    bgm = objectMapper.readValue(session.getCurrentEpisodeBgmJson(), com.story.game.gameplay.dto.BgmDto.class);
                    log.debug("Using stored BGM for episode (same episode): mood={}", bgm.getMood());
                } else {
                    log.warn("No BGM stored for current episode");
                }
            } catch (Exception e) {
                log.warn("Failed to retrieve BGM for current episode: {}", e.getMessage());
            }

            return buildGameStateResponse(session, currentNode.getEpisode().getStory(), currentNode.getEpisode(), nextNode, false, nodeImage, bgm);
        } else {
            return handleEpisodeEnd(session);
        }
    }

    private GameStateResponseDto handleEpisodeEnd(GameSession session) {
        Episode currentEpisode = episodeRepository.findById(UUID.fromString(session.getCurrentEpisodeId()))
                .orElseThrow(() -> new RuntimeException("Episode not found"));
        StoryCreation storyCreation = currentEpisode.getStory();

        List<EpisodeEnding> endings = episodeEndingRepository.findByEpisode(currentEpisode);
        EpisodeEnding matchedEnding = evaluateEpisodeEnding(endings, session.getAccumulatedTags());

        if (matchedEnding != null && matchedEnding.getGaugeChanges() != null) {
            try {
                Map<String, Integer> gaugeChanges = objectMapper.readValue(matchedEnding.getGaugeChanges(),
                        new TypeReference<Map<String, Integer>>() {});
                for (Map.Entry<String, Integer> change : gaugeChanges.entrySet()) {
                    session.getGaugeStates().merge(change.getKey(), change.getValue(), Integer::sum);
                    int newValue = Math.max(0, Math.min(100, session.getGaugeStates().get(change.getKey())));
                    session.getGaugeStates().put(change.getKey(), newValue);
                }
            } catch (Exception e) {
                log.error("Failed to parse gauge changes", e);
            }
        }

        session.getCompletedEpisodes().add(currentEpisode.getId().toString());

        Optional<Episode> nextEpisodeOpt = episodeRepository.findByStoryAndOrder(storyCreation, currentEpisode.getOrder() + 1);

        if (nextEpisodeOpt.isPresent()) {
            Episode nextEpisode = nextEpisodeOpt.get();
            StoryNode rootNode = storyNodeRepository.findByEpisodeAndDepth(nextEpisode, 0)
                    .orElseThrow(() -> new RuntimeException("Root node not found for next episode"));

            session.setCurrentEpisodeId(nextEpisode.getId().toString());
            session.setCurrentNodeId(rootNode.getId().toString());
            session.getVisitedNodes().add(rootNode.getId().toString());
            session.setAccumulatedTags(new HashMap<>());
            gameSessionRepository.save(session);

            NodeImageInfo nodeImage = generateNodeImage(
                    session.getStoryCreationId(),
                    rootNode.getId().toString(),
                    storyMapper.toStoryNodeDto(rootNode),
                    storyMapper.toEpisodeDto(nextEpisode)
            );
            // Get BGM for next episode (episode-based)
            com.story.game.gameplay.dto.BgmDto bgm = null;
            try {
                bgm = bgmService.getBgmForEpisode(
                    session.getStoryDataId(),
                    nextEpisode.getId(),
                    nextEpisode.getTitle(),
                    nextEpisode.getIntroText()
                );

                // Save BGM to session for this new episode
                if (bgm != null) {
                    session.setCurrentEpisodeBgmJson(objectMapper.writeValueAsString(bgm));
                    gameSessionRepository.save(session);
                    log.info("BGM set for next episode {}: mood={}", nextEpisode.getId(), bgm.getMood());
                }
            } catch (Exception e) {
                log.warn("Failed to get BGM for next episode: {}", e.getMessage());
            }

            GameStateResponseDto response = buildGameStateResponse(session, storyCreation, nextEpisode, rootNode, true, nodeImage, bgm);
            response.setIsEpisodeEnd(true);
            response.setEpisodeEnding(storyMapper.toEpisodeEndingDto(matchedEnding));
            return response;
        } else {
            return handleGameEnd(session, storyCreation, matchedEnding);
        }
    }

    private GameStateResponseDto handleGameEnd(GameSession session, StoryCreation storyCreation, EpisodeEnding lastEpisodeEnding) {
        FinalEndingDto matchedFinalEnding = evaluateFinalEnding(storyCreation, session.getGaugeStates());

        session.setIsCompleted(true);
        session.setFinalEndingId(matchedFinalEnding != null ? matchedFinalEnding.getId() : "default_end");
        gameSessionRepository.save(session);

        // Update user achievements after game completion
        if (session.getUser() != null) {
            try {
                achievementService.checkAndUpdateAchievements(session.getUser());
                log.info("Achievement progress updated for user {} after game completion", session.getUser().getUsername());
            } catch (Exception e) {
                log.error("Failed to update achievements for user {}: {}",
                    session.getUser().getUsername(), e.getMessage(), e);
                // Continue anyway - achievement update failure shouldn't break game flow
            }
        }

        return handleGameEndResponse(session, storyCreation, lastEpisodeEnding, matchedFinalEnding);
    }

    // [수정] 프론트엔드가 엔딩 텍스트를 표시할 수 있도록 nodeText에도 엔딩 내용을 매핑합니다.
    private GameStateResponseDto handleGameEndResponse(GameSession session, StoryCreation storyCreation, EpisodeEnding lastEpisodeEnding, FinalEndingDto matchedFinalEnding) {
        // [수정] 필터링된 게이지 정보 사용
        List<GaugeDto> gaugeDefinitions = getFilteredGauges(storyCreation);

        // [추가] 엔딩 내용을 UI가 표시하는 필드(nodeText)에 복사
        String displayTitle = "THE END";
        String displayText = "이야기가 끝났습니다.";

        if (matchedFinalEnding != null) {
            displayTitle = matchedFinalEnding.getTitle();
            displayText = matchedFinalEnding.getSummary();
        } else if (lastEpisodeEnding != null) {
            displayTitle = lastEpisodeEnding.getTitle();
            displayText = lastEpisodeEnding.getText();
        }

        // Get BGM from session (maintain last episode BGM for ending screen)
        com.story.game.gameplay.dto.BgmDto bgm = null;
        try {
            if (session.getCurrentEpisodeBgmJson() != null) {
                bgm = objectMapper.readValue(session.getCurrentEpisodeBgmJson(), com.story.game.gameplay.dto.BgmDto.class);
                log.debug("Using stored BGM for game ending: mood={}", bgm.getMood());
            }
        } catch (Exception e) {
            log.warn("Failed to retrieve BGM for game ending: {}", e.getMessage());
        }

        return GameStateResponseDto.builder()
                .sessionId(session.getId())
                .currentEpisodeId(session.getCurrentEpisodeId())
                .gaugeStates(session.getGaugeStates())
                .accumulatedTags(session.getAccumulatedTags())
                .gaugeDefinitions(gaugeDefinitions)

                // [핵심 수정] 엔딩 제목과 내용을 UI 표시 필드에 설정
                .episodeTitle(displayTitle)
                .nodeText(displayText)

                .choices(Collections.emptyList())
                .bgm(bgm)
                .isEpisodeEnd(true)
                .isGameEnd(true) // 이 플래그 덕분에 프론트엔드가 엔딩 화면으로 인식함
                .episodeEnding(storyMapper.toEpisodeEndingDto(lastEpisodeEnding))
                .finalEnding(matchedFinalEnding)
                .build();
    }

    // [SpEL 적용] 조건 평가 로직 개선
    private boolean evaluateCondition(String condition, Map<String, Integer> values) {
        if (condition == null || condition.isBlank() || "default".equals(condition)) {
            return true;
        }
        try {
            String expr = condition.replace("AND", "&&").replace("OR", "||");

            StandardEvaluationContext context = new StandardEvaluationContext();
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }

            for (String key : values.keySet()) {
                String pattern = "(?<!#)\\b" + java.util.regex.Pattern.quote(key) + "\\b";
                expr = expr.replaceAll(pattern, "#" + key);
            }

            return Boolean.TRUE.equals(parser.parseExpression(expr).getValue(context, Boolean.class));

        } catch (Exception e) {
            log.warn("Failed to evaluate condition with SpEL: {} -> {}", condition, e.getMessage());
            return false;
        }
    }

    private EpisodeEnding evaluateEpisodeEnding(List<EpisodeEnding> endings, Map<String, Integer> accumulatedTags) {
        if (endings == null || endings.isEmpty()) return null;
        for (EpisodeEnding ending : endings) {
            if (evaluateCondition(ending.getCondition(), accumulatedTags)) {
                return ending;
            }
        }
        return endings.get(0);
    }

    private FinalEndingDto evaluateFinalEnding(StoryCreation storyCreation, Map<String, Integer> gaugeStates) {
        if (storyCreation.getEndingConfigJson() == null) {
            log.error("Final Ending Config is NULL for story: {}", storyCreation.getId());
            return null;
        }
        try {
            List<FinalEndingDto> finalEndings = objectMapper.readValue(storyCreation.getEndingConfigJson(),
                    new TypeReference<List<FinalEndingDto>>() {});

            for (FinalEndingDto ending : finalEndings) {
                if (evaluateCondition(ending.getCondition(), gaugeStates)) {
                    log.info("Matched Final Ending: {}", ending.getTitle());
                    return ending;
                }
            }
            return finalEndings.isEmpty() ? null : finalEndings.get(finalEndings.size() - 1);
        } catch (Exception e) {
            log.error("Failed to evaluate final endings", e);
            return null;
        }
    }

    private FinalEndingDto findFinalEndingById(StoryCreation storyCreation, String endingId) {
        if (storyCreation.getEndingConfigJson() == null) return null;
        try {
            List<FinalEndingDto> finalEndings = objectMapper.readValue(storyCreation.getEndingConfigJson(),
                    new TypeReference<List<FinalEndingDto>>() {});
            return finalEndings.stream()
                    .filter(e -> e.getId().equals(endingId))
                    .findFirst()
                    .orElse(null);
        } catch(Exception e) {
            return null;
        }
    }

    private GameStateResponseDto buildGameStateResponse(GameSession session, StoryCreation storyCreation,
                                                        Episode episode, StoryNode node, boolean showIntro, NodeImageInfo nodeImage, com.story.game.gameplay.dto.BgmDto bgm) {
        List<StoryChoice> choices = storyChoiceRepository.findBySourceNodeOrderByChoiceOrderAsc(node);
        List<StoryChoiceDto> choiceDtos = choices.stream()
                .map(storyMapper::toStoryChoiceDto)
                .collect(Collectors.toList());

        // [수정] 필터링된 게이지 정보만 전송
        List<GaugeDto> gaugeDefinitions = getFilteredGauges(storyCreation);

        StoryNodeDto nodeDto = storyMapper.toStoryNodeDto(node);
        StoryNodeDto.StoryNodeDetailDto nodeDetails = nodeDto != null ? nodeDto.getDetails() : null;

        String nodeText = node.getText();
        String introText = showIntro ? episode.getIntroText() : null;

        // [디버깅] 응답 내용 로깅
        log.info("=== Building Game State Response ===");
        log.info("Episode: {} (order: {})", episode.getTitle(), episode.getOrder());
        log.info("Node ID: {}, Depth: {}", node.getId(), node.getDepth());
        log.info("Show Intro: {}", showIntro);
        log.info("Intro Text: {}", introText != null ? introText.substring(0, Math.min(50, introText.length())) + "..." : "null");
        log.info("Node Text: {}", nodeText != null ? nodeText.substring(0, Math.min(50, nodeText.length())) + "..." : "null");
        log.info("Choices Count: {}", choiceDtos.size());
        log.info("Node Image: type={}, url={}", nodeImage != null ? nodeImage.getType() : "null", nodeImage != null ? nodeImage.getImageUrl() : "null");
        log.info("====================================");

        return GameStateResponseDto.builder()
                .sessionId(session.getId())
                .characterId(session.getSelectedCharacterId())
                .currentEpisodeId(session.getCurrentEpisodeId())
                .currentNodeId(session.getCurrentNodeId())
                .gaugeStates(session.getGaugeStates())
                .accumulatedTags(session.getAccumulatedTags())
                .episodeTitle(episode.getTitle())
                .introText(introText)
                .nodeText(nodeText)
                .nodeDetails(nodeDetails)
                .choices(choiceDtos)
                .nodeImage(nodeImage)
                .bgm(bgm)
                .gaugeDefinitions(gaugeDefinitions)
                .isEpisodeEnd(false)
                .isGameEnd(false)
                .build();
    }

    // [추가] 선택된 게이지만 필터링하는 메서드
    private List<GaugeDto> getFilteredGauges(StoryCreation storyCreation) {
        try {
            List<GaugeDto> allGauges = new ArrayList<>();
            if (storyCreation.getGaugesJson() != null) {
                allGauges = objectMapper.readValue(storyCreation.getGaugesJson(),
                        new TypeReference<List<GaugeDto>>() {});
            }

            List<String> selectedIds = new ArrayList<>();
            if (storyCreation.getSelectedGaugeIdsJson() != null) {
                selectedIds = objectMapper.readValue(storyCreation.getSelectedGaugeIdsJson(),
                        new TypeReference<List<String>>() {});
            }

            if (selectedIds.isEmpty()) return allGauges;

            List<String> finalSelectedIds = selectedIds;
            return allGauges.stream()
                    .filter(g -> finalSelectedIds.contains(g.getId()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Failed to filter gauges", e);
            return Collections.emptyList();
        }
    }

    private NodeImageInfo generateNodeImage(String storyId, String nodeId, StoryNodeDto node, EpisodeDto episode) {
        // Determine image type based on node properties
        ImageType imageType = determineImageType(node);

        // Check if image already exists in database
        Optional<StoryNode> nodeOpt = storyNodeRepository.findById(UUID.fromString(nodeId));
        if (nodeOpt.isPresent() && nodeOpt.get().getImageFileKey() != null) {
            String rawFileKey = nodeOpt.get().getImageFileKey();

            // Extract fileKey if it's a full URL (legacy data issue)
            String fileKey = extractFileKeyFromUrl(rawFileKey);

            log.info("DB image_file_key for node {}: {} -> extracted: {}",
                nodeId, rawFileKey, fileKey);

            // Image exists, generate presigned download URL
            String presignedUrl = s3Service.generatePresignedDownloadUrl(fileKey);
            log.info("Using existing image for node {}: {}", nodeId,
                presignedUrl.substring(0, Math.min(100, presignedUrl.length())) + "...");

            return NodeImageInfo.builder()
                    .imageUrl(presignedUrl)
                    .type(imageType)
                    .fileKey(fileKey)
                    .altText(generateAltText(node, imageType))
                    .build();
        }

        // Image doesn't exist - don't generate during gameplay
        // Images should only be generated during story creation
        log.info("No existing image found for node {}. Image generation during gameplay is disabled.", nodeId);
        return null;
    }

    /**
     * Extract S3 file key from full URL or return as-is if already a key
     * Handles legacy data where full URLs were stored instead of keys
     */
    private String extractFileKeyFromUrl(String fileKeyOrUrl) {
        if (fileKeyOrUrl == null) {
            return null;
        }

        // If it's a full URL, extract the key part after bucket name
        if (fileKeyOrUrl.startsWith("http://") || fileKeyOrUrl.startsWith("https://")) {
            try {
                // Pattern: https://bucket.s3.region.amazonaws.com/key
                // Extract everything after the first "/" following ".amazonaws.com"
                int amazonIdx = fileKeyOrUrl.indexOf(".amazonaws.com/");
                if (amazonIdx != -1) {
                    return fileKeyOrUrl.substring(amazonIdx + ".amazonaws.com/".length());
                }

                // Fallback: extract everything after the third "/"
                int slashCount = 0;
                for (int i = 0; i < fileKeyOrUrl.length(); i++) {
                    if (fileKeyOrUrl.charAt(i) == '/') {
                        slashCount++;
                        if (slashCount == 3) {
                            return fileKeyOrUrl.substring(i + 1);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract fileKey from URL: {}", fileKeyOrUrl, e);
            }
        }

        // Already a key, return as-is
        return fileKeyOrUrl;
    }

    /**
     * Determine image type based on node properties
     */
    private ImageType determineImageType(StoryNodeDto node) {
        if (node.getDepth() != null && node.getDepth() == 0) {
            return ImageType.EPISODE_START;
        }
        // For other nodes, it's a regular scene image
        // Ending images are handled separately in ending DTOs
        return ImageType.SCENE;
    }

    /**
     * Generate alt text for accessibility
     */
    private String generateAltText(StoryNodeDto node, ImageType imageType) {
        StringBuilder altText = new StringBuilder();

        switch (imageType) {
            case EPISODE_START:
                altText.append("Episode start: ");
                break;
            case SCENE:
                altText.append("Scene: ");
                break;
            default:
                altText.append("Image: ");
        }

        String nodeText = node.getText();
        if (nodeText != null && nodeText.length() > 100) {
            altText.append(nodeText, 0, 100).append("...");
        } else {
            altText.append(nodeText);
        }

        return altText.toString();
    }

    @Transactional
    public StoryData saveStoryData(String title, String description, String storyJson) {
        try {
            FullStoryDto fullStory = objectMapper.readValue(storyJson, FullStoryDto.class);
            String fileKey = "stories/" + UUID.randomUUID().toString() + ".json";

            Integer totalEpisodes = fullStory.getMetadata() != null ? fullStory.getMetadata().getTotalEpisodes() : 0;
            Integer totalNodes = fullStory.getMetadata() != null ? fullStory.getMetadata().getTotalNodes() : 0;

            StoryData storyData = StoryData.builder()
                    .title(title)
                    .description(description)
                    .storyFileKey(fileKey)
                    .totalEpisodes(totalEpisodes)
                    .totalNodes(totalNodes)
                    .build();

            return storyDataRepository.save(storyData);
        } catch (Exception e) {
            log.error("Failed to parse or save story data", e);
            throw new com.story.game.common.exception.InvalidStateException("Failed to save story data: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<StoryData> getAllStories() {
        List<StoryData> stories = storyDataRepository.findAll();

        // TODO: Consider implementing pagination to avoid loading all stories into memory
        if (stories.size() > 100) {
            log.warn("Loading {} stories into memory. Consider implementing pagination for better performance.", stories.size());
        }

        // Generate presigned URLs for thumbnails
        for (StoryData story : stories) {
            if (story.getThumbnailFileKey() != null && !story.getThumbnailFileKey().isEmpty()) {
                try {
                    // Extract fileKey if it's a full URL (legacy data)
                    String fileKey = extractFileKeyFromUrl(story.getThumbnailFileKey());

                    // Generate presigned download URL
                    String presignedUrl = s3Service.generatePresignedDownloadUrl(fileKey);

                    // Set the presigned URL to thumbnailUrl (for frontend display)
                    // Note: This modifies the entity but doesn't persist to DB
                    story.setThumbnailUrl(presignedUrl);
                    log.debug("Generated presigned URL for story {} thumbnail: {}",
                        story.getId(), presignedUrl.substring(0, Math.min(100, presignedUrl.length())) + "...");
                } catch (Exception e) {
                    log.warn("Failed to generate presigned URL for story {} thumbnail: {}",
                        story.getId(), e.getMessage());
                    // Keep original value if presigned URL generation fails
                }
            }
        }

        return stories;
    }

    @Transactional(readOnly = true)
    public FullStoryDto getStoryDataById(Long storyDataId) {
        StoryData storyData = storyDataRepository.findById(storyDataId)
                .orElseThrow(() -> new RuntimeException("Story data not found: " + storyDataId));
        StoryCreation storyCreation = storyCreationRepository.findByStoryDataId(storyData.getId())
                .orElseThrow(() -> new RuntimeException("StoryCreation not found"));
        return storyMapper.buildFullStoryDtoFromDb(storyCreation);
    }

    /**
     * 최종 엔딩 정보만 조회하는 전용 API
     * 게임 완료 후 엔딩 화면 표시용
     */
    @Transactional(readOnly = true)
    public com.story.game.gameplay.dto.FinalEndingResponseDto getFinalEnding(String sessionId, com.story.game.auth.entity.User user) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
            throw new com.story.game.common.exception.UnauthorizedException("You don't have permission to access this game session");
        }

        if (!Boolean.TRUE.equals(session.getIsCompleted())) {
            throw new com.story.game.common.exception.InvalidStateException("Game is not completed yet. sessionId: " + sessionId);
        }

        StoryCreation storyCreation = storyCreationRepository.findByStoryDataId(session.getStoryDataId())
                .orElseThrow(() -> new RuntimeException("StoryCreation not found"));

        FinalEndingDto matchedFinalEnding = null;
        if (session.getFinalEndingId() != null) {
            matchedFinalEnding = findFinalEndingById(storyCreation, session.getFinalEndingId());
        }

        List<GaugeDto> gaugeDefinitions = getFilteredGauges(storyCreation);

        return com.story.game.gameplay.dto.FinalEndingResponseDto.builder()
                .sessionId(session.getId())
                .isCompleted(session.getIsCompleted())
                .finalEnding(matchedFinalEnding)
                .finalGaugeStates(session.getGaugeStates())
                .gaugeDefinitions(gaugeDefinitions)
                .completedEpisodesCount(session.getCompletedEpisodes() != null ? session.getCompletedEpisodes().size() : 0)
                .build();
    }


    /**
     * 게임 진행 상황을 텍스트로 변환
     * JSON 형식의 노드 정보(NPC 감정, 관계 변화 등)를 파싱하여 포함
     */
    private String buildProgressContent(StoryChoice choice, StoryNode fromNode, StoryNode toNode) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== 게임 진행 상황 ===").append("\n\n");

        // 이전 노드
        sb.append("이전 상황:\n");
        sb.append(fromNode.getText()).append("\n\n");

        // 플레이어 선택
        sb.append("플레이어의 선택:\n");
        sb.append("'").append(choice.getText()).append("'").append("\n\n");

        // 선택의 태그 정보
        if (choice.getTags() != null && !choice.getTags().isEmpty()) {
            try {
                List<String> tags = objectMapper.readValue(choice.getTags(), new TypeReference<List<String>>() {});
                if (!tags.isEmpty()) {
                    sb.append("선택의 의미: ").append(String.join(", ", tags)).append("\n\n");
                }
            } catch (Exception e) {
                log.debug("Failed to parse choice tags for progress content", e);
            }
        }

        // 즉각 반응
        if (choice.getImmediateReaction() != null && !choice.getImmediateReaction().isEmpty()) {
            sb.append("선택 직후 반응:\n");
            sb.append(choice.getImmediateReaction()).append("\n\n");
        }

        // 현재 노드
        sb.append("현재 상황:\n");
        sb.append(toNode.getText()).append("\n");

        // 상황 정보
        if (toNode.getSituation() != null && !toNode.getSituation().isEmpty()) {
            sb.append("\n상세 상황: ").append(toNode.getSituation()).append("\n");
        }

        // NPC 감정 정보 파싱
        if (toNode.getNpcEmotions() != null && !toNode.getNpcEmotions().isEmpty()) {
            try {
                Map<String, String> emotions = objectMapper.readValue(toNode.getNpcEmotions(),
                        new TypeReference<Map<String, String>>() {});
                if (!emotions.isEmpty()) {
                    sb.append("\nNPC 감정 상태:\n");
                    emotions.forEach((npc, emotion) ->
                        sb.append("- ").append(npc).append(": ").append(emotion).append("\n")
                    );
                }
            } catch (Exception e) {
                log.debug("Failed to parse NPC emotions for progress content", e);
            }
        }

        // 관계 변화 정보 파싱
        if (toNode.getRelationsUpdate() != null && !toNode.getRelationsUpdate().isEmpty()) {
            try {
                Map<String, String> relations = objectMapper.readValue(toNode.getRelationsUpdate(),
                        new TypeReference<Map<String, String>>() {});
                if (!relations.isEmpty()) {
                    sb.append("\n관계 변화:\n");
                    relations.forEach((character, change) ->
                        sb.append("- ").append(character).append(": ").append(change).append("\n")
                    );
                }
            } catch (Exception e) {
                log.debug("Failed to parse relations update for progress content", e);
            }
        }

        // 에피소드 정보
        if (toNode.getEpisode() != null) {
            sb.append("\n에피소드: ").append(toNode.getEpisode().getTitle());
        }

        return sb.toString();
    }

    /**
     * Extract character ID from selected characters JSON
     * Used for RAG integration - extracts the first character ID
     */
    private String extractCharacterIdFromJson(String selectedCharactersJson) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(selectedCharactersJson);
            if (root.isArray() && root.size() > 0) {
                return root.get(0).asText();
            }
            throw new IllegalStateException("No character selected");
        } catch (Exception e) {
            log.error("Failed to parse character selection JSON", e);
            throw new IllegalStateException("Failed to parse character selection", e);
        }
    }

    /**
     * Get selected characters by StoryData ID
     * 프론트엔드가 StoryData ID로 선택된 캐릭터를 조회할 수 있도록 지원
     */
    @Transactional(readOnly = true)
    public com.story.game.creation.dto.SelectedCharactersResponseDto getSelectedCharactersByStoryDataId(Long storyDataId) {
        log.info("=== Get Selected Characters by StoryDataId ===");
        log.info("StoryDataId: {}", storyDataId);

        // StoryData ID로 StoryCreation 찾기
        StoryCreation storyCreation = storyCreationRepository.findByStoryDataId(storyDataId)
                .orElseThrow(() -> new RuntimeException("StoryCreation not found for StoryDataId: " + storyDataId));

        log.info("Found StoryCreation ID: {}", storyCreation.getId());

        // 선택된 캐릭터 확인
        if (storyCreation.getSelectedCharactersForChatJson() == null ||
            storyCreation.getSelectedCharactersForChatJson().isBlank()) {
            return com.story.game.creation.dto.SelectedCharactersResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .storyDataId(storyDataId)
                    .chatCharacterId(storyCreation.getId())  // NPC 대화용 - storyId와 동일
                    .hasSelection(false)
                    .selectedCharacterNames(List.of())
                    .selectedCharacters(List.of())
                    .build();
        }

        try {
            // 선택된 캐릭터 이름 파싱
            List<String> selectedNames = objectMapper.readValue(
                    storyCreation.getSelectedCharactersForChatJson(),
                    new TypeReference<List<String>>() {}
            );

            // 전체 캐릭터 파싱
            List<com.story.game.common.dto.CharacterDto> allCharacters = new ArrayList<>();
            if (storyCreation.getCharactersJson() != null && !storyCreation.getCharactersJson().isBlank()) {
                allCharacters = objectMapper.readValue(
                        storyCreation.getCharactersJson(),
                        new TypeReference<List<com.story.game.common.dto.CharacterDto>>() {}
                );
            }

            // 선택된 캐릭터만 필터링하고 각 캐릭터에 chatCharacterId 할당
            List<com.story.game.common.dto.CharacterDto> selectedCharacters = allCharacters.stream()
                    .filter(c -> selectedNames.contains(c.getName()))
                    .map(c -> {
                        // Generate unique chatCharacterId for each character
                        String chatCharId = storyCreation.getId() + "_" + c.getName();
                        return com.story.game.common.dto.CharacterDto.builder()
                                .name(c.getName())
                                .aliases(c.getAliases())
                                .description(c.getDescription())
                                .relationships(c.getRelationships())
                                .chatCharacterId(chatCharId)  // Assign unique ID
                                .build();
                    })
                    .collect(Collectors.toList());

            return com.story.game.creation.dto.SelectedCharactersResponseDto.builder()
                    .storyId(storyCreation.getId())
                    .storyDataId(storyDataId)
                    .chatCharacterId(null)  // Deprecated - 이제 각 캐릭터가 고유 ID를 가짐
                    .hasSelection(true)
                    .selectedCharacterNames(selectedNames)
                    .selectedCharacters(selectedCharacters)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse selected characters for StoryDataId: {}", storyDataId, e);
            throw new com.story.game.common.exception.InvalidStateException("Failed to retrieve selected characters: " + e.getMessage());
        }
    }
}