package com.story.game.story.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.EpisodeDto;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.dto.StoryChoiceDto;
import com.story.game.common.dto.StoryNodeDto;
import com.story.game.creation.entity.StoryCreation;
import com.story.game.story.entity.Episode;
import com.story.game.story.entity.EpisodeEnding;
import com.story.game.story.entity.StoryChoice;
import com.story.game.story.entity.StoryNode;
import com.story.game.story.repository.EpisodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoryMapper {

    private final ObjectMapper objectMapper;
    private final EpisodeRepository episodeRepository;

    @Transactional
    public void saveEpisodeDtoToDb(EpisodeDto episodeDto, StoryCreation storyCreation) {
        Episode episode = toEpisodeEntity(episodeDto, storyCreation);
        if (episodeDto.getNodes() != null && !episodeDto.getNodes().isEmpty()) {
            StoryNode rootNode = toStoryNodeEntityRecursive(episodeDto.getNodes().get(0), episode, null);
            List<StoryNode> allNodes = new ArrayList<>();
            flattenNodeTree(rootNode, allNodes, new HashSet<>());
            episode.setNodes(allNodes);
        }
        episodeRepository.save(episode);
    }

    private void flattenNodeTree(StoryNode node, List<StoryNode> flatList, Set<StoryNode> visited) {
        if (node == null || !visited.add(node)) { // Cycle detection
            return;
        }
        flatList.add(node);
        if (node.getOutgoingChoices() != null) {
            for (StoryChoice choice : node.getOutgoingChoices()) {
                flattenNodeTree(choice.getDestinationNode(), flatList, visited);
            }
        }
    }

    @Transactional(readOnly = true)
    public FullStoryDto buildFullStoryDtoFromDb(StoryCreation storyCreation) {
        List<Episode> episodes = episodeRepository.findAllByStory(storyCreation);
        List<EpisodeDto> episodeDtos = episodes.stream()
                .map(this::toEpisodeDto)
                .collect(Collectors.toList());

        // Build metadata
        int totalNodes = (int) episodes.stream()
                .flatMap(ep -> ep.getNodes().stream())
                .count();

        List<String> tempGaugeIds = new ArrayList<>();
        if (storyCreation.getSelectedGaugeIdsJson() != null) {
            try {
                tempGaugeIds = objectMapper.readValue(storyCreation.getSelectedGaugeIdsJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse selected gauge IDs", e);
            }
        }
        final List<String> gaugeIds = tempGaugeIds;

        List<com.story.game.common.dto.GaugeDto> gauges = new ArrayList<>();
        if (storyCreation.getGaugesJson() != null) {
            try {
                gauges = objectMapper.readValue(storyCreation.getGaugesJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, com.story.game.common.dto.GaugeDto.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse gauges", e);
            }
        }

        FullStoryDto.MetadataDto metadata = FullStoryDto.MetadataDto.builder()
                .totalEpisodes(episodes.size())
                .totalNodes(totalNodes)
                .gauges(gaugeIds)
                .characterCount(0) // Can be calculated if needed
                .build();

        // Build context
        List<com.story.game.common.dto.CharacterDto> characters = new ArrayList<>();
        if (storyCreation.getCharactersJson() != null) {
            try {
                characters = objectMapper.readValue(storyCreation.getCharactersJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, com.story.game.common.dto.CharacterDto.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse characters", e);
            }
        }

        List<com.story.game.common.dto.GaugeDto> selectedGauges = new ArrayList<>();
        if (!gaugeIds.isEmpty() && !gauges.isEmpty()) {
            selectedGauges = gauges.stream()
                    .filter(g -> gaugeIds.contains(g.getId()))
                    .collect(Collectors.toList());
        }

        List<com.story.game.common.dto.FinalEndingDto> finalEndings = new ArrayList<>();
        if (storyCreation.getEndingConfigJson() != null) {
            try {
                finalEndings = objectMapper.readValue(storyCreation.getEndingConfigJson(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, com.story.game.common.dto.FinalEndingDto.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse final endings", e);
            }
        }

        FullStoryDto.ContextDto context = FullStoryDto.ContextDto.builder()
                .novelSummary(storyCreation.getSummary())
                .characters(characters)
                .selectedGauges(selectedGauges)
                .finalEndings(finalEndings)
                .build();

        return FullStoryDto.builder()
                .metadata(metadata)
                .context(context)
                .episodes(episodeDtos)
                .build();
    }

    @Transactional
    public void addChildrenToNode(StoryNode sourceNode, List<StoryNodeDto> childDtos) {
        Episode episode = sourceNode.getEpisode();
        int currentSize = sourceNode.getOutgoingChoices().size();
        for (int i = 0; i < childDtos.size(); i++) {
            StoryNodeDto childDto = childDtos.get(i);
            // Simplified: assumes the first choice in the DTO corresponds to the edge to this child.
            // A more robust implementation might involve matching choices to children via an ID.
            StoryChoiceDto choiceDto;
            if (childDto.getChoices() != null && !childDto.getChoices().isEmpty()) {
                choiceDto = childDto.getChoices().get(0);
            } else {
                choiceDto = StoryChoiceDto.builder().text("Continue").build();
            }

            StoryChoice choice = toStoryChoiceEntity(choiceDto, sourceNode, currentSize + i);
            StoryNode childNode = toStoryNodeEntityRecursive(childDto, episode, choice);
            choice.setDestinationNode(childNode);
            sourceNode.getOutgoingChoices().add(choice);
        }
    }

    private Episode toEpisodeEntity(EpisodeDto dto, StoryCreation storyCreation) {
        Episode episode = Episode.builder()
                .story(storyCreation)
                .title(dto.getTitle())
                .order(dto.getOrder())
                .description(dto.getDescription())
                .theme(dto.getTheme())
                .introText(dto.getIntroText())
                .build();

        if (dto.getEndings() != null) {
            List<EpisodeEnding> endings = dto.getEndings().stream()
                    .map(endingDto -> toEpisodeEndingEntity(endingDto, episode))
                    .collect(Collectors.toList());
            episode.setEndings(endings);
        }

        return episode;
    }

    private StoryNode toStoryNodeEntityRecursive(StoryNodeDto dto, Episode episode, StoryChoice parentChoice) {
        // Extract details and convert to JSON strings
        String situationStr = null;
        String npcEmotionsJson = null;
        String relationsUpdateJson = null;

        if (dto.getDetails() != null) {
            situationStr = dto.getDetails().getSituation();

            if (dto.getDetails().getNpcEmotions() != null) {
                try {
                    npcEmotionsJson = objectMapper.writeValueAsString(dto.getDetails().getNpcEmotions());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize npc emotions for node", e);
                }
            }

            if (dto.getDetails().getRelationsUpdate() != null) {
                try {
                    relationsUpdateJson = objectMapper.writeValueAsString(dto.getDetails().getRelationsUpdate());
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize relations update for node", e);
                }
            }
        }

        StoryNode node = StoryNode.builder()
                .episode(episode)
                .parentChoice(parentChoice)
                .depth(dto.getDepth())
                .text(dto.getText())
                .nodeType(dto.getNodeType())
                .situation(situationStr)
                .npcEmotions(npcEmotionsJson)
                .relationsUpdate(relationsUpdateJson)
                .build();

        if (dto.getChoices() != null && dto.getChildren() != null) {
            List<StoryNodeDto> childrenDtos = dto.getChildren();
            List<StoryChoiceDto> choicesDtos = dto.getChoices();

            for (int i = 0; i < choicesDtos.size(); i++) {
                StoryChoiceDto choiceDto = choicesDtos.get(i);
                StoryNodeDto childNodeDto = (i < childrenDtos.size()) ? childrenDtos.get(i) : null;

                StoryChoice choice = toStoryChoiceEntity(choiceDto, node, i);
                if (childNodeDto != null) {
                    StoryNode childNode = toStoryNodeEntityRecursive(childNodeDto, episode, choice);
                    choice.setDestinationNode(childNode);
                }
                node.getOutgoingChoices().add(choice);
            }
        }
        return node;
    }

    private StoryChoice toStoryChoiceEntity(StoryChoiceDto dto, StoryNode sourceNode, int order) {
        String tagsJson = null;
        if (dto.getTags() != null) {
            try {
                tagsJson = objectMapper.writeValueAsString(dto.getTags());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize tags for choice: {}", dto.getText(), e);
            }
        }

        return StoryChoice.builder()
                .sourceNode(sourceNode)
                .text(dto.getText())
                .choiceOrder(order)
                .tags(tagsJson)
                .immediateReaction(dto.getImmediateReaction())
                .build();
    }

    private EpisodeEnding toEpisodeEndingEntity(com.story.game.common.dto.EpisodeEndingDto dto, Episode episode) {
        String gaugeChangesJson = null;
        if (dto.getGaugeChanges() != null) {
            try {
                gaugeChangesJson = objectMapper.writeValueAsString(dto.getGaugeChanges());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize gauge changes for ending: {}", dto.getTitle(), e);
            }
        }
        return EpisodeEnding.builder()
                .episode(episode)
                .aiGeneratedId(dto.getId())  // AI가 생성한 ID 저장
                .title(dto.getTitle())
                .condition(dto.getCondition())
                .text(dto.getText())
                .gaugeChanges(gaugeChangesJson)
                .build();
    }

    // DTO Conversion methods
    public EpisodeDto toEpisodeDto(Episode episode) {
        if (episode == null) {
            return null;
        }

        return EpisodeDto.builder()
                .id(episode.getId().toString())
                .title(episode.getTitle())
                .order(episode.getOrder())
                .description(episode.getDescription())
                .theme(episode.getTheme())
                .introText(episode.getIntroText())
                .nodes(episode.getNodes() != null ? episode.getNodes().stream().map(this::toStoryNodeDto).collect(Collectors.toList()) : Collections.emptyList())
                .endings(episode.getEndings() != null ? episode.getEndings().stream().map(this::toEpisodeEndingDto).collect(Collectors.toList()) : Collections.emptyList())
                .build();
    }

    public StoryNodeDto toStoryNodeDto(StoryNode node) {
        return toStoryNodeDto(node, new HashSet<>());
    }

    private StoryNodeDto toStoryNodeDto(StoryNode node, Set<UUID> visitedNodes) {
        if (node == null) {
            return null;
        }

        // Prevent circular reference by checking if node was already visited
        if (visitedNodes.contains(node.getId())) {
            log.warn("Circular reference detected for node: {}", node.getId());
            return StoryNodeDto.builder()
                    .id(node.getId().toString())
                    .depth(node.getDepth())
                    .text(node.getText())
                    .nodeType(node.getNodeType())
                    .choices(Collections.emptyList())
                    .children(Collections.emptyList())
                    .build();
        }

        visitedNodes.add(node.getId());

        // Build details from entity fields
        StoryNodeDto.StoryNodeDetailDto details = buildNodeDetails(node);

        StoryNodeDto.StoryNodeDtoBuilder builder = StoryNodeDto.builder()
                .id(node.getId().toString())
                .depth(node.getDepth())
                .text(node.getText())
                .nodeType(node.getNodeType())
                .details(details)
                .choices(
                        node.getOutgoingChoices() != null ?
                        node.getOutgoingChoices().stream()
                                .map(this::toStoryChoiceDto)
                                .collect(Collectors.toList())
                        : Collections.emptyList()
                );

        // Recursively map children with visited nodes tracking
        List<StoryNodeDto> children = node.getOutgoingChoices().stream()
                .map(StoryChoice::getDestinationNode)
                .map(childNode -> toStoryNodeDto(childNode, visitedNodes))
                .collect(Collectors.toList());
        builder.children(children);

        return builder.build();
    }

    private StoryNodeDto.StoryNodeDetailDto buildNodeDetails(StoryNode node) {
        if (node.getSituation() == null && node.getNpcEmotions() == null && node.getRelationsUpdate() == null) {
            return null;
        }

        Map<String, String> npcEmotionsMap = null;
        if (node.getNpcEmotions() != null) {
            try {
                npcEmotionsMap = objectMapper.readValue(node.getNpcEmotions(),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse npc emotions for node: {}", node.getId(), e);
            }
        }

        Map<String, String> relationsUpdateMap = null;
        if (node.getRelationsUpdate() != null) {
            try {
                relationsUpdateMap = objectMapper.readValue(node.getRelationsUpdate(),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to parse relations update for node: {}", node.getId(), e);
            }
        }

        return StoryNodeDto.StoryNodeDetailDto.builder()
                .situation(node.getSituation())
                .npcEmotions(npcEmotionsMap)
                .relationsUpdate(relationsUpdateMap)
                .build();
    }

    public StoryChoiceDto toStoryChoiceDto(StoryChoice choice) {
        if (choice == null) {
            return null;
        }

        List<String> tags = new ArrayList<>();
        if (choice.getTags() != null) {
            try {
                tags = objectMapper.readValue(choice.getTags(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize tags for choice: {}", choice.getText(), e);
            }
        }

        return StoryChoiceDto.builder()
                .text(choice.getText())
                .tags(tags)
                .immediateReaction(choice.getImmediateReaction())
                .build();
    }

    public com.story.game.common.dto.EpisodeEndingDto toEpisodeEndingDto(EpisodeEnding ending) {
        if (ending == null) {
            return null;
        }

        Map<String, Integer> gaugeChanges = Collections.emptyMap();
        if(ending.getGaugeChanges() != null) {
            try {
                gaugeChanges = objectMapper.readValue(ending.getGaugeChanges(), objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize gauge changes for ending: {}", ending.getTitle(), e);
            }
        }

        return com.story.game.common.dto.EpisodeEndingDto.builder()
                .id(ending.getId().toString())
                .title(ending.getTitle())
                .condition(ending.getCondition())
                .text(ending.getText())
                .gaugeChanges(gaugeChanges)
                .build();
    }
}
