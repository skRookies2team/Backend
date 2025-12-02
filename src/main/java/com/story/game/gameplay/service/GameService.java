package com.story.game.gameplay.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.EpisodeDto;
import com.story.game.common.dto.EpisodeEndingDto;
import com.story.game.common.dto.FinalEndingDto;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.GaugeDto;
import com.story.game.common.dto.StoryChoiceDto;
import com.story.game.common.dto.StoryNodeDto;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.gameplay.dto.GameStateResponseDto;
import com.story.game.gameplay.entity.GameSession;
import com.story.game.gameplay.repository.GameSessionRepository;
import com.story.game.infrastructure.s3.S3Service;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final S3Service s3Service;
    private final com.story.game.ai.service.RelayServerClient relayServerClient;

    @Transactional
    public GameStateResponseDto startGame(Long storyDataId) {
        StoryData storyData = storyDataRepository.findById(storyDataId)
            .orElseThrow(() -> new RuntimeException("Story not found: " + storyDataId));

        FullStoryDto fullStory = getFullStory(storyData);

        // Initialize gauge states with default values (50)
        Map<String, Integer> initialGauges = new HashMap<>();
        for (GaugeDto gauge : fullStory.getContext().getSelectedGauges()) {
            initialGauges.put(gauge.getId(), 50);
        }

        // Get first episode
        EpisodeDto firstEpisode = fullStory.getEpisodes().stream()
            .min(Comparator.comparingInt(EpisodeDto::getOrder))
            .orElseThrow(() -> new RuntimeException("No episodes found"));

        // Get root node (depth 0)
        StoryNodeDto rootNode = findRootNode(firstEpisode);

        // Create new game session
        GameSession session = GameSession.builder()
            .storyDataId(storyDataId)
            .currentEpisodeId(firstEpisode.getId())
            .currentNodeId(rootNode.getId())
            .gaugeStates(initialGauges)
            .accumulatedTags(new HashMap<>())
            .visitedNodes(new ArrayList<>(List.of(rootNode.getId())))
            .completedEpisodes(new ArrayList<>())
            .build();

        session = gameSessionRepository.save(session);

        // Generate image for the first node
        String imageUrl = generateNodeImage(rootNode, firstEpisode);

        return buildGameStateResponse(session, fullStory, firstEpisode, rootNode, true, imageUrl);
    }

    @Transactional(readOnly = true)
    public GameStateResponseDto getGameState(String sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        StoryData storyData = storyDataRepository.findById(session.getStoryDataId())
            .orElseThrow(() -> new RuntimeException("Story not found"));

        FullStoryDto fullStory = getFullStory(storyData);
        EpisodeDto currentEpisode = findEpisodeById(fullStory, session.getCurrentEpisodeId());
        StoryNodeDto currentNode = findNodeById(currentEpisode, session.getCurrentNodeId());

        boolean isFirstNode = session.getVisitedNodes().size() == 1;

        // Generate image for the current node
        String imageUrl = generateNodeImage(currentNode, currentEpisode);

        return buildGameStateResponse(session, fullStory, currentEpisode, currentNode, isFirstNode, imageUrl);
    }

    @Transactional
    public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex) {
        GameSession session = gameSessionRepository.findById(sessionId)
            .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));

        if (session.getIsCompleted()) {
            throw new RuntimeException("Game is already completed");
        }

        StoryData storyData = storyDataRepository.findById(session.getStoryDataId())
            .orElseThrow(() -> new RuntimeException("Story not found"));

        FullStoryDto fullStory = getFullStory(storyData);
        EpisodeDto currentEpisode = findEpisodeById(fullStory, session.getCurrentEpisodeId());
        StoryNodeDto currentNode = findNodeById(currentEpisode, session.getCurrentNodeId());

        // Validate choice
        if (currentNode.getChoices() == null || choiceIndex >= currentNode.getChoices().size()) {
            throw new RuntimeException("Invalid choice index: " + choiceIndex);
        }

        StoryChoiceDto selectedChoice = currentNode.getChoices().get(choiceIndex);

        // Accumulate tags from the choice
        if (selectedChoice.getTags() != null) {
            for (String tag : selectedChoice.getTags()) {
                session.getAccumulatedTags().merge(tag, 1, Integer::sum);
            }
        }

        // Find next node
        StoryNodeDto nextNode = findNextNode(currentEpisode, currentNode.getId(), choiceIndex);

        if (nextNode != null) {
            // Move to next node
            session.setCurrentNodeId(nextNode.getId());
            session.getVisitedNodes().add(nextNode.getId());
            session = gameSessionRepository.save(session);

            // Generate image for the next node
            String imageUrl = generateNodeImage(nextNode, currentEpisode);

            return buildGameStateResponse(session, fullStory, currentEpisode, nextNode, false, imageUrl);
        } else {
            // Reached leaf node - evaluate episode ending
            return handleEpisodeEnd(session, fullStory, currentEpisode);
        }
    }

    private GameStateResponseDto handleEpisodeEnd(GameSession session, FullStoryDto fullStory,
        EpisodeDto currentEpisode) {
        // Evaluate episode endings based on accumulated tags
        EpisodeEndingDto matchedEnding = evaluateEpisodeEnding(currentEpisode,
            session.getAccumulatedTags());

        if (matchedEnding != null && matchedEnding.getGaugeChanges() != null) {
            // Apply gauge changes
            for (Map.Entry<String, Integer> change : matchedEnding.getGaugeChanges().entrySet()) {
                session.getGaugeStates().merge(change.getKey(), change.getValue(), Integer::sum);
                // Clamp between 0 and 100
                int newValue = Math.max(0,
                    Math.min(100, session.getGaugeStates().get(change.getKey())));
                session.getGaugeStates().put(change.getKey(), newValue);
            }
        }

        // Mark episode as completed
        session.getCompletedEpisodes().add(currentEpisode.getId());

        // Check for next episode
        EpisodeDto nextEpisode = findNextEpisode(fullStory, currentEpisode.getOrder());

        if (nextEpisode != null) {
            // Move to next episode
            session.setCurrentEpisodeId(nextEpisode.getId());
            StoryNodeDto rootNode = findRootNode(nextEpisode);
            session.setCurrentNodeId(rootNode.getId());
            session.getVisitedNodes().add(rootNode.getId());
            session.setAccumulatedTags(new HashMap<>()); // Reset tags for new episode
            session = gameSessionRepository.save(session);

            // Generate image for the next episode's root node
            String imageUrl = generateNodeImage(rootNode, nextEpisode);

            GameStateResponseDto response = buildGameStateResponse(session, fullStory, nextEpisode,
                rootNode, true, imageUrl);
            response.setIsEpisodeEnd(true);
            response.setEpisodeEnding(matchedEnding);
            return response;
        } else {
            // Game completed - evaluate final ending
            return handleGameEnd(session, fullStory, matchedEnding);
        }
    }

    private GameStateResponseDto handleGameEnd(GameSession session, FullStoryDto fullStory,
        EpisodeEndingDto lastEpisodeEnding) {
        FinalEndingDto matchedFinalEnding = evaluateFinalEnding(fullStory,
            session.getGaugeStates());

        session.setIsCompleted(true);
        session.setFinalEndingId(matchedFinalEnding != null ? matchedFinalEnding.getId() : null);
        session = gameSessionRepository.save(session);

        GameStateResponseDto response = GameStateResponseDto.builder()
            .sessionId(session.getId())
            .gaugeStates(session.getGaugeStates())
            .accumulatedTags(session.getAccumulatedTags())
            .gaugeDefinitions(fullStory.getContext().getSelectedGauges())
            .isEpisodeEnd(true)
            .isGameEnd(true)
            .episodeEnding(lastEpisodeEnding)
            .finalEnding(matchedFinalEnding)
            .build();

        return response;
    }

    private GameStateResponseDto buildGameStateResponse(GameSession session, FullStoryDto fullStory,
        EpisodeDto episode, StoryNodeDto node, boolean showIntro, String imageUrl) {
        return GameStateResponseDto.builder()
            .sessionId(session.getId())
            .currentEpisodeId(session.getCurrentEpisodeId())
            .currentNodeId(session.getCurrentNodeId())
            .gaugeStates(session.getGaugeStates())
            .accumulatedTags(session.getAccumulatedTags())
            .episodeTitle(episode.getTitle())
            .introText(showIntro ? episode.getIntroText() : null)
            .nodeText(node.getText())
            .nodeDetails(node.getDetails())
            .choices(node.getChoices())
            .imageUrl(imageUrl)
            .gaugeDefinitions(fullStory.getContext().getSelectedGauges())
            .isEpisodeEnd(false)
            .isGameEnd(false)
            .build();
    }

    /**
     * Generate image for a story node via relay server
     */
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
            // Return null if image generation fails - game can continue without image
            return null;
        }
    }

    private FullStoryDto getFullStory(StoryData storyData) {
        String storyJson = s3Service.downloadFileContent(storyData.getStoryFileKey());
        return parseStoryJson(storyJson);
    }

    private FullStoryDto parseStoryJson(String json) {
        try {
            return objectMapper.readValue(json, FullStoryDto.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse story JSON", e);
        }
    }

    private StoryNodeDto findRootNode(EpisodeDto episode) {
        return episode.getNodes().stream()
            .filter(node -> node.getDepth() == 0)
            .findFirst()
            .orElseThrow(
                () -> new RuntimeException("Root node not found in episode: " + episode.getId()));
    }

    private EpisodeDto findEpisodeById(FullStoryDto story, String episodeId) {
        return story.getEpisodes().stream()
            .filter(ep -> ep.getId().equals(episodeId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Episode not found: " + episodeId));
    }

    private StoryNodeDto findNodeById(EpisodeDto episode, String nodeId) {
        return episode.getNodes().stream()
            .filter(node -> node.getId().equals(nodeId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Node not found: " + nodeId));
    }

    private StoryNodeDto findNextNode(EpisodeDto episode, String parentId, int choiceIndex) {
        // Find child nodes with matching parent_id
        List<StoryNodeDto> children = episode.getNodes().stream()
            .filter(node -> parentId.equals(node.getParentId()))
            .collect(Collectors.toList());

        if (children.isEmpty()) {
            return null; // Leaf node reached
        }

        // Return the child corresponding to the choice index
        if (choiceIndex < children.size()) {
            return children.get(choiceIndex);
        }

        return children.get(0); // Fallback to first child
    }

    private EpisodeDto findNextEpisode(FullStoryDto story, int currentOrder) {
        return story.getEpisodes().stream()
            .filter(ep -> ep.getOrder() > currentOrder)
            .min(Comparator.comparingInt(EpisodeDto::getOrder))
            .orElse(null);
    }

    private EpisodeEndingDto evaluateEpisodeEnding(EpisodeDto episode,
        Map<String, Integer> accumulatedTags) {
        if (episode.getEndings() == null || episode.getEndings().isEmpty()) {
            return null;
        }

        for (EpisodeEndingDto ending : episode.getEndings()) {
            if (evaluateCondition(ending.getCondition(), accumulatedTags)) {
                return ending;
            }
        }

        // Return first ending as default
        return episode.getEndings().get(0);
    }

    private FinalEndingDto evaluateFinalEnding(FullStoryDto story,
        Map<String, Integer> gaugeStates) {
        if (story.getContext().getFinalEndings() == null) {
            return null;
        }

        for (FinalEndingDto ending : story.getContext().getFinalEndings()) {
            if (evaluateCondition(ending.getCondition(), gaugeStates)) {
                return ending;
            }
        }

        // Return first ending as default
        return story.getContext().getFinalEndings().isEmpty() ? null :
            story.getContext().getFinalEndings().get(0);
    }

    private boolean evaluateCondition(String condition, Map<String, Integer> values) {
        if (condition == null || condition.isBlank()) {
            return true;
        }

        try {
            // Simple condition parser for expressions like "hope >= 70 AND trust >= 60"
            String expr = condition
                .replace("AND", "&&")
                .replace("OR", "||")
                .replace(">=", ">=")
                .replace("<=", "<=");

            // Replace variable names with values
            for (Map.Entry<String, Integer> entry : values.entrySet()) {
                expr = expr.replace(entry.getKey(), String.valueOf(entry.getValue()));
            }

            // Evaluate using JavaScript engine
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("JavaScript");
            if (engine == null) {
                // Fallback: simple evaluation
                return simpleEvaluate(condition, values);
            }
            Object result = engine.eval(expr);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Failed to evaluate condition: {}", condition, e);
            return false;
        }
    }

    private boolean simpleEvaluate(String condition, Map<String, Integer> values) {
        // Simple parser for conditions like "cooperative>=2 AND trusting>=1"
        String[] andParts = condition.split("\\s+AND\\s+");

        for (String part : andParts) {
            part = part.trim();
            if (!evaluateSingleCondition(part, values)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateSingleCondition(String condition, Map<String, Integer> values) {
        // Parse conditions like "hope >= 70" or "cooperative>=2"
        String[] operators = {">=", "<=", ">", "<", "=="};

        for (String op : operators) {
            if (condition.contains(op)) {
                String[] parts = condition.split(op);
                if (parts.length == 2) {
                    String varName = parts[0].trim();
                    int threshold = Integer.parseInt(parts[1].trim());
                    int value = values.getOrDefault(varName, 0);

                    return switch (op) {
                        case ">=" -> value >= threshold;
                        case "<=" -> value <= threshold;
                        case ">" -> value > threshold;
                        case "<" -> value < threshold;
                        case "==" -> value == threshold;
                        default -> false;
                    };
                }
            }
        }
        return true;
    }

    // Story data management
    @Transactional
    public StoryData saveStoryData(String title, String description, String storyJson) {
        String fileKey = "stories/" + UUID.randomUUID().toString() + ".json";
        s3Service.uploadFile(fileKey, storyJson);
        FullStoryDto fullStory = parseStoryJson(storyJson);

        StoryData storyData = StoryData.builder()
            .title(title)
            .description(description)
            .storyFileKey(fileKey)
            .totalEpisodes(fullStory.getMetadata().getTotalEpisodes())
            .totalNodes(fullStory.getMetadata().getTotalNodes())
            .build();

        return storyDataRepository.save(storyData);
    }

    public List<StoryData> getAllStories() {
        return storyDataRepository.findAll();
    }

    /**
     * Get full story data by storyDataId (for frontend game composition)
     */
    @Transactional(readOnly = true)
    public FullStoryDto getStoryDataById(Long storyDataId) {
        StoryData storyData = storyDataRepository.findById(storyDataId)
            .orElseThrow(() -> new RuntimeException("Story data not found: " + storyDataId));

        return getFullStory(storyData);
    }
}
