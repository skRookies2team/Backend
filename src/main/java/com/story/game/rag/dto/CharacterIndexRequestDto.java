package com.story.game.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterIndexRequestDto {

    @NotBlank(message = "Character ID is required")
    @Size(max = 100)
    private String characterId;

    @NotBlank(message = "Character name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 2000)
    private String description;

    @Size(max = 1000)
    private String personality;

    @Size(max = 2000)
    private String background;

    private List<String> dialogueSamples;
    private Map<String, String> relationships;
    private Map<String, Object> additionalInfo;
}
