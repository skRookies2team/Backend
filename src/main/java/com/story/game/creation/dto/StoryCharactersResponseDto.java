package com.story.game.creation.dto;

import com.story.game.common.dto.*;
import com.story.game.creation.entity.StoryCreation;
import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryCharactersResponseDto {

    private String storyId;
    private StoryCreation.CreationStatus status;
    private List<CharacterDto> characters;
}
