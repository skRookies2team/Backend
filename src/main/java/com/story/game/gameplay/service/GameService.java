package com.story.game.gameplay.service;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
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

    @Transactional
    public GameStateResponseDto startGame(Long storyDataId, com.story.game.auth.entity.User user) {
        StoryData storyData = storyDataRepository.findById(storyDataId)
            .orElseThrow(() -> new RuntimeException("Story not found: " + storyDataId));
        StoryCreation storyCreation = storyCreationRepository.findByStoryDataId(storyData.getId())
            .orElseThrow(() -> new RuntimeException("StoryCreation not found for StoryData: " + storyData.getId()));

        Episode firstEpisode = episodeRepository.findByStoryAndOrder(storyCreation, 1)
            .orElseThrow(() -> new RuntimeException("First episode not found"));

        StoryNode rootNode = storyNodeRepository.findByEpisodeAndDepth(firstEpisode, 0)
            .orElseThrow(() -> new RuntimeException("Root node not found"));

        Map<String, Integer> initialGauges = new HashMap<>();
        try {
            // Get selected gauge IDs
            List<String> selectedGaugeIds = new ArrayList<>();
            if (storyCreation.getSelectedGaugeIdsJson() != null) {
                selectedGaugeIds = objectMapper.readValue(storyCreation.getSelectedGaugeIdsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }

            // Initialize only selected gauges
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

        // Verify user owns this session
        if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: You don't have permission to access this game session");
        }

        StoryCreation storyCreation = storyCreationRepository.findByStoryDataId(session.getStoryDataId())
            .orElseThrow(() -> new RuntimeException("StoryCreation not found"));

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

        // Verify user owns this session
        if (session.getUser() != null && !session.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized: You don't have permission to modify this game session");
        }

        if (session.getIsCompleted()) {
            log.info("Game is already completed. Returning final state.");
            return getGameState(sessionId, user);
        }

        StoryNode currentNode = storyNodeRepository.findById(UUID.fromString(session.getCurrentNodeId()))
            .orElseThrow(() -> new RuntimeException("Current node not found: " + session.getCurrentNodeId()));

        List<StoryChoice> choices = storyChoiceRepository.findBySourceNodeOrderByChoiceOrderAsc(currentNode);

        if (choices.isEmpty()) {
            log.info("Leaf node reached (no choices). Ending episode.");
            return handleEpisodeEnd(session);
        }

        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            throw new RuntimeException("Invalid choice index: " + choiceIndex + " (valid range: 0-" + (choices.size() - 1) + ")");
        }

        StoryChoice selectedChoice = choices.get(choiceIndex);

        if (selectedChoice.getTags() != null) {
            try {
                List<String> tags = objectMapper.readValue(selectedChoice.getTags(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
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
            gameSessionRepository.save(session);

            // Check if the next node is a leaf node (ending node with no choices)
            List<StoryChoice> nextNodeChoices = storyChoiceRepository.findBySourceNodeOrderByChoiceOrderAsc(nextNode);
            if (nextNodeChoices.isEmpty()) {
                log.info("Next node is a leaf node (ending node). Triggering episode end.");
                return handleEpisodeEnd(session);
            }

            String imageUrl = generateNodeImage(storyMapper.toStoryNodeDto(nextNode), storyMapper.toEpisodeDto(currentNode.getEpisode()));

            return buildGameStateResponse(session, currentNode.getEpisode().getStory(), currentNode.getEpisode(), nextNode, false, imageUrl);
        } else {
            return handleEpisodeEnd(session);
        }
    }

    private GameStateResponseDto handleEpisodeEnd(GameSession session) {
        log.info("=== Handle Episode End ===");
        Episode currentEpisode = episodeRepository.findById(UUID.fromString(session.getCurrentEpisodeId()))
            .orElseThrow(() -> new RuntimeException("Episode not found: " + session.getCurrentEpisodeId()));
        StoryCreation storyCreation = currentEpisode.getStory();

        log.info("Current Episode: order={}, title={}", currentEpisode.getOrder(), currentEpisode.getTitle());

        List<EpisodeEnding> endings = episodeEndingRepository.findByEpisode(currentEpisode);
        log.info("Found {} episode endings", endings.size());
        EpisodeEnding matchedEnding = evaluateEpisodeEnding(endings, session.getAccumulatedTags());
        log.info("Matched ending: {}", matchedEnding != null ? matchedEnding.getTitle() : "null");

        if (matchedEnding != null && matchedEnding.getGaugeChanges() != null) {
            try {
                Map<String, Integer> gaugeChanges = objectMapper.readValue(matchedEnding.getGaugeChanges(),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));
                for (Map.Entry<String, Integer> change : gaugeChanges.entrySet()) {
                    session.getGaugeStates().merge(change.getKey(), change.getValue(), Integer::sum);
                    int newValue = Math.max(0, Math.min(100, session.getGaugeStates().get(change.getKey())));
                    session.getGaugeStates().put(change.getKey(), newValue);
                }
            } catch (Exception e) {
                log.error("Failed to parse gauge changes for ending {}", matchedEnding.getId(), e);
            }
        }

        session.getCompletedEpisodes().add(currentEpisode.getId().toString());

        log.info("Looking for next episode: order={}", currentEpisode.getOrder() + 1);
        Optional<Episode> nextEpisodeOpt = episodeRepository.findByStoryAndOrder(storyCreation, currentEpisode.getOrder() + 1);

        if (nextEpisodeOpt.isPresent()) {
            Episode nextEpisode = nextEpisodeOpt.get();
            log.info("✅ Next episode found: order={}, title={}", nextEpisode.getOrder(), nextEpisode.getTitle());

            StoryNode rootNode = storyNodeRepository.findByEpisodeAndDepth(nextEpisode, 0)
                .orElseThrow(() -> new RuntimeException("Root node not found for episode: " + nextEpisode.getId()));
            log.info("Root node found for next episode: {}", rootNode.getId());

            session.setCurrentEpisodeId(nextEpisode.getId().toString());
            session.setCurrentNodeId(rootNode.getId().toString());
            session.getVisitedNodes().add(rootNode.getId().toString());
            session.setAccumulatedTags(new HashMap<>());
            gameSessionRepository.save(session);

            String imageUrl = generateNodeImage(storyMapper.toStoryNodeDto(rootNode), storyMapper.toEpisodeDto(nextEpisode));
            GameStateResponseDto response = buildGameStateResponse(session, storyCreation, nextEpisode, rootNode, true, imageUrl);
            response.setIsEpisodeEnd(true);
            response.setEpisodeEnding(storyMapper.toEpisodeEndingDto(matchedEnding));

            log.info("Returning response with isEpisodeEnd=true and next episode data");
            return response;
        } else {
            log.info("❌ No next episode found. Game ends here.");
            return handleGameEnd(session, storyCreation, matchedEnding);
        }
    }

    private GameStateResponseDto handleGameEnd(GameSession session, StoryCreation storyCreation, EpisodeEnding lastEpisodeEnding) {
        FinalEndingDto matchedFinalEnding = evaluateFinalEnding(storyCreation, session.getGaugeStates());

        session.setIsCompleted(true);
        session.setFinalEndingId(matchedFinalEnding != null ? matchedFinalEnding.getId() : null);
        gameSessionRepository.save(session);

        List<GaugeDto> gaugeDefinitions = new ArrayList<>();
         try {
            if (storyCreation.getGaugesJson() != null) {
                gaugeDefinitions = objectMapper.readValue(storyCreation.getGaugesJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, GaugeDto.class));
            }
        } catch (Exception e) {
            log.error("Failed to parse gauge definitions from StoryCreation", e);
        }

        return GameStateResponseDto.builder()
            .sessionId(session.getId())
            .gaugeStates(session.getGaugeStates())
            .accumulatedTags(session.getAccumulatedTags())
            .gaugeDefinitions(gaugeDefinitions)
            .choices(Collections.emptyList())  // Ensure choices is never null
            .isEpisodeEnd(true)
            .isGameEnd(true)
            .episodeEnding(storyMapper.toEpisodeEndingDto(lastEpisodeEnding))
            .finalEnding(matchedFinalEnding)
            .build();
    }

    private GameStateResponseDto buildGameStateResponse(GameSession session, StoryCreation storyCreation,
                                                        Episode episode, StoryNode node, boolean showIntro, String imageUrl) {
        List<StoryChoice> choices = storyChoiceRepository.findBySourceNodeOrderByChoiceOrderAsc(node);
        List<StoryChoiceDto> choiceDtos = choices.stream()
                .map(storyMapper::toStoryChoiceDto)
                .collect(Collectors.toList());

        List<GaugeDto> gaugeDefinitions = new ArrayList<>();
        try {
            if (storyCreation.getGaugesJson() != null) {
                gaugeDefinitions = objectMapper.readValue(storyCreation.getGaugesJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, GaugeDto.class));
            }
        } catch (Exception e) {
            log.error("Failed to parse gauge definitions from StoryCreation", e);
        }

        // Get node details from mapper
        StoryNodeDto nodeDto = storyMapper.toStoryNodeDto(node);
        StoryNodeDto.StoryNodeDetailDto nodeDetails = nodeDto != null ? nodeDto.getDetails() : null;

        return GameStateResponseDto.builder()
                .sessionId(session.getId())
                .currentEpisodeId(session.getCurrentEpisodeId())
                .currentNodeId(session.getCurrentNodeId())
                .gaugeStates(session.getGaugeStates())
                .accumulatedTags(session.getAccumulatedTags())
                .episodeTitle(episode.getTitle())
                .introText(showIntro ? episode.getIntroText() : null)
                .nodeText(node.getText())
                .nodeDetails(nodeDetails)
                .choices(choiceDtos)
                .imageUrl(imageUrl)
                .gaugeDefinitions(gaugeDefinitions)
                .isEpisodeEnd(false)
                .isGameEnd(false)
                .build();
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
            log.error("Failed to generate image for node {}: {}", node.getId(), e.getMessage());
            return null;
        }
    }

    private EpisodeEnding evaluateEpisodeEnding(List<EpisodeEnding> endings, Map<String, Integer> accumulatedTags) {
        if (endings == null || endings.isEmpty()) {
            return null;
        }
        for (EpisodeEnding ending : endings) {
            if (evaluateCondition(ending.getCondition(), accumulatedTags)) {
                return ending;
            }
        }
        return endings.get(0);
    }

    private FinalEndingDto evaluateFinalEnding(StoryCreation storyCreation, Map<String, Integer> gaugeStates) {
        if (storyCreation.getEndingConfigJson() == null) {
            return null;
        }
        try {
            List<FinalEndingDto> finalEndings = objectMapper.readValue(storyCreation.getEndingConfigJson(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, FinalEndingDto.class));

            for (FinalEndingDto ending : finalEndings) {
                if (evaluateCondition(ending.getCondition(), gaugeStates)) {
                    return ending;
                }
            }
            return finalEndings.isEmpty() ? null : finalEndings.get(0);
        } catch (Exception e) {
            log.error("Failed to evaluate final endings", e);
            return null;
        }
    }

    private boolean evaluateCondition(String condition, Map<String, Integer> values) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        try {
            String expr = condition.replace("AND", "&&").replace("OR", "||");

            // Use word boundary regex to prevent partial matches
            // e.g., "love" won't match in "lovely"
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                String pattern = "\\b" + java.util.regex.Pattern.quote(entry.getKey()) + "\\b";
                expr = expr.replaceAll(pattern, String.valueOf(entry.getValue()));
            }

            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            Object result = engine.eval(expr);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}", condition, e);
            return false;
        }
    }

    @Transactional
    public StoryData saveStoryData(String title, String description, String storyJson) {
        try {
            FullStoryDto fullStory = objectMapper.readValue(storyJson, FullStoryDto.class);
            String fileKey = "stories/" + UUID.randomUUID().toString() + ".json";
            // s3Service.uploadFile(fileKey, storyJson); // This might be desired for backup

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
}
