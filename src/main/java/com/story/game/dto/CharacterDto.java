package com.story.game.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterDto {
    private String name;
    private List<String> aliases;
    private String description;
    private List<String> relationships;
}
