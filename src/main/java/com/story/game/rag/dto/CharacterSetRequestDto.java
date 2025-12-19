package com.story.game.rag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for setting/updating character information
 * Sent to relay-server's /ai/chat/set-character endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterSetRequestDto {

    @NotBlank(message = "Character ID is required")
    private String characterId;

    @NotBlank(message = "Character name is required")
    private String characterName;

    private String characterDescription;  // Optional: character traits, personality, etc.
}
