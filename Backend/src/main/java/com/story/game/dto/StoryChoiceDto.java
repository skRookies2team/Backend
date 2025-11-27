package com.story.game.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryChoiceDto {
    private String text;
    private List<String> tags;
}
