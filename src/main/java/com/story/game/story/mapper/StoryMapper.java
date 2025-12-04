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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StoryMapper {

    private final ObjectMapper objectMapper;
    private final EpisodeRepository episodeRepository;

    @Transactional
    public void saveEpisodeDtoToDb(EpisodeDto episodeDto, StoryCreation storyCreation) {
        Episode episode = toEpisodeEntity(episodeDto, storyCreation);
        if (episodeDto.getNodes() != null && !episodeDto.getNodes().isEmpty()) {
            // Assuming the first node is the root
            StoryNode rootNode = toStoryNodeEntityRecursive(episodeDto.getNodes().get(0), episode, null);
            episode.setNodes(Collections.singletonList(rootNode));
        }
        episodeRepository.save(episode);
    }

    @Transactional(readOnly = true)
    public FullStoryDto buildFullStoryDtoFromDb(StoryCreation storyCreation) {
        List<Episode> episodes = episodeRepository.findAllByStory(storyCreation);
        List<EpisodeDto> episodeDtos = episodes.stream()
                .map(this::toEpisodeDto)
                .collect(Collectors.toList());

        FullStoryDto fullStoryDto = new FullStoryDto();
        fullStoryDto.setEpisodes(episodeDtos);
        // Map other FullStoryDto fields if necessary
        return fullStoryDto;
    }

    @Transactional
    public void addChildrenToNode(StoryNode sourceNode, List<StoryNodeDto> childDtos) {
        Episode episode = sourceNode.getEpisode();
        for (StoryNodeDto childDto : childDtos) {
            // Simplified: assumes the first choice in the DTO corresponds to the edge to this child.
            // A more robust implementation might involve matching choices to children via an ID.
            StoryChoiceDto choiceDto = (childDto.getChoices() != null && !childDto.getChoices().isEmpty())
                    ? childDto.getChoices().get(0)
                    : StoryChoiceDto.builder().text("Continue").build();

            StoryChoice choice = toStoryChoiceEntity(choiceDto, sourceNode);
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
        StoryNode node = StoryNode.builder()
                .episode(episode)
                .parentChoice(parentChoice)
                .depth(dto.getDepth())
                .text(dto.getText())
                .nodeType(dto.getNodeType())
                .build();

        if (dto.getChoices() != null && dto.getChildren() != null) {
            List<StoryNodeDto> childrenDtos = dto.getChildren();
            List<StoryChoiceDto> choicesDtos = dto.getChoices();

            for (int i = 0; i < choicesDtos.size(); i++) {
                StoryChoiceDto choiceDto = choicesDtos.get(i);
                StoryNodeDto childNodeDto = (i < childrenDtos.size()) ? childrenDtos.get(i) : null;

                StoryChoice choice = toStoryChoiceEntity(choiceDto, node);
                if (childNodeDto != null) {
                    StoryNode childNode = toStoryNodeEntityRecursive(childNodeDto, episode, choice);
                    choice.setDestinationNode(childNode);
                }
                node.getOutgoingChoices().add(choice);
            }
        }
        return node;
    }

    private StoryChoice toStoryChoiceEntity(StoryChoiceDto dto, StoryNode sourceNode) {
        String tagsJson = null;
        if (dto.getTags() != null) {
            try {
                tagsJson = objectMapper.writeValueAsString(dto.getTags());
            } catch (JsonProcessingException e) { /* Log error */ }
        }

        return StoryChoice.builder()
                .sourceNode(sourceNode)
                .text(dto.getText())
                .tags(tagsJson)
                .build();
    }

    private EpisodeEnding toEpisodeEndingEntity(com.story.game.common.dto.EpisodeEndingDto dto, Episode episode) {
        String gaugeChangesJson = null;
        if (dto.getGaugeChanges() != null) {
            try {
                gaugeChangesJson = objectMapper.writeValueAsString(dto.getGaugeChanges());
            } catch (JsonProcessingException e) { /* Log error */ }
        }
        return EpisodeEnding.builder()
                .episode(episode)
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
        if (node == null) {
            return null;
        }

        StoryNodeDto.StoryNodeDtoBuilder builder = StoryNodeDto.builder()
                .id(node.getId().toString())
                .depth(node.getDepth())
                .text(node.getText())
                .nodeType(node.getNodeType())
                .choices(node.getOutgoingChoices() != null ? node.getOutgoingChoices().stream().map(this::toStoryChoiceDto).collect(Collectors.toList()) : Collections.emptyList());

        // Recursively map children
        List<StoryNodeDto> children = node.getOutgoingChoices().stream()
                .map(StoryChoice::getDestinationNode)
                .map(this::toStoryNodeDto)
                .collect(Collectors.toList());
        builder.children(children);

        return builder.build();
    }

    private StoryChoiceDto toStoryChoiceDto(StoryChoice choice) {
        if (choice == null) {
            return null;
        }

        List<String> tags = new ArrayList<>();
        if (choice.getTags() != null) {
            try {
                tags = objectMapper.readValue(choice.getTags(), objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (JsonProcessingException e) {
                // Handle exception or log it
            }
        }

        return StoryChoiceDto.builder()
                .text(choice.getText())
                .tags(tags)
                .build();
    }

    private com.story.game.common.dto.EpisodeEndingDto toEpisodeEndingDto(EpisodeEnding ending) {
        if (ending == null) {
            return null;
        }

        Map<String, Integer> gaugeChanges = Collections.emptyMap();
        if(ending.getGaugeChanges() != null) {
            try {
                gaugeChanges = objectMapper.readValue(ending.getGaugeChanges(), objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Integer.class));
            } catch (JsonProcessingException e) {
                // Handle or log exception
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
