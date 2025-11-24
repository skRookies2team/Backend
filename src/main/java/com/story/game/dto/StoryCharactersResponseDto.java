package com.story.game.dto;

import com.story.game.entity.StoryCreation;
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
