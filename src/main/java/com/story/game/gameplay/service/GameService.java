package com.story.game.gameplay.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.FinalEndingDto;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.GaugeDto;
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

    // [SpEL] 파서 인스턴스 생성
    private final ExpressionParser parser = new SpelExpressionParser();
    private final RagService ragService;

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
                .currentEpisodeId(firstEpisode.getId().toString())
                .currentNodeId(rootNode.getId().toString())
                .gaugeStates(initialGauges)
                .accumulatedTags(new HashMap<>())
                .visitedNodes(new ArrayList<>(List.of(rootNode.getId().toString())))
                .completedEpisodes(new ArrayList<>())
                .build();

        session = gameSessionRepository.save(session);
        log.info("Game session created for user: {} with sessionId: {}", user.getUsername(), session.getId());

        String imageUrl = generateNodeImage(storyMapper.toStoryNodeDto(rootNode), storyMapper.toEpisodeDto(firstEpisode));

        return buildGameStateResponse(session, storyCreation, firstEpisode, rootNode, true, imageUrl);
    }

    @Transactional(readOnly = true)
    public GameStateResponseDto getGameState(String sessionId, com.story.game.auth.entity.User user) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: You don't have permission to access this game session");
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

        String imageUrl = generateNodeImage(storyMapper.toStoryNodeDto(currentNode), storyMapper.toEpisodeDto(currentEpisode));

        return buildGameStateResponse(session, storyCreation, currentEpisode, currentNode, isFirstNodeOfEpisode, imageUrl);
    }

    @Transactional
    public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex, com.story.game.auth.entity.User user) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: You don't have permission to modify this game session");
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
            throw new RuntimeException("Invalid choice index: " + choiceIndex);
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

            String imageUrl = generateNodeImage(storyMapper.toStoryNodeDto(nextNode), storyMapper.toEpisodeDto(currentNode.getEpisode()));

            return buildGameStateResponse(session, currentNode.getEpisode().getStory(), currentNode.getEpisode(), nextNode, false, imageUrl);
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

            String imageUrl = generateNodeImage(storyMapper.toStoryNodeDto(rootNode), storyMapper.toEpisodeDto(nextEpisode));
            GameStateResponseDto response = buildGameStateResponse(session, storyCreation, nextEpisode, rootNode, true, imageUrl);
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
                                                        Episode episode, StoryNode node, boolean showIntro, String imageUrl) {
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
        log.info("====================================");

        return GameStateResponseDto.builder()
                .sessionId(session.getId())
                .currentEpisodeId(session.getCurrentEpisodeId())
                .currentNodeId(session.getCurrentNodeId())
                .gaugeStates(session.getGaugeStates())
                .accumulatedTags(session.getAccumulatedTags())
                .episodeTitle(episode.getTitle())
                .introText(introText)
                .nodeText(nodeText)
                .nodeDetails(nodeDetails)
                .choices(choiceDtos)
                .imageUrl(imageUrl)
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

    private String generateNodeImage(StoryNodeDto node, EpisodeDto episode) {
        try {
            com.story.game.ai.dto.ImageGenerationRequestDto request = com.story.game.ai.dto.ImageGenerationRequestDto.builder()
                    .nodeText(node.getText())
                    .situation(node.getDetails() != null ? node.getDetails().getSituation() : null)
                    .npcEmotions(node.getDetails() != null ? node.getDetails().getNpcEmotions() : null)
                    .episodeTitle(episode.getTitle())
                    .episodeOrder(episode.getOrder())
                    .nodeDepth(node.getDepth())
                    .build();

            com.story.game.ai.dto.ImageGenerationResponseDto response = relayServerClient.generateImage(request);
            return response.getImageUrl();
        } catch (Exception e) {
            log.warn("Failed to generate image: {}", e.getMessage());
            return null;
        }
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
            throw new RuntimeException("Failed to save story data: " + e.getMessage(), e);
        }
    }

    public List<StoryData> getAllStories() {
        return storyDataRepository.findAll();
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
            throw new RuntimeException("Unauthorized: You don't have permission to access this game session");
        }

        if (!Boolean.TRUE.equals(session.getIsCompleted())) {
            throw new RuntimeException("Game is not completed yet. sessionId: " + sessionId);
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
            sb.append("\n상세 상황: ").append(toNode.getSituation());
        }

        return sb.toString();
    }
}