package com.story.game.creation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NovelAnalysisRequestDto {

    @NotBlank(message = "Novel text is required")
    @JsonProperty("novel_text")
    private String novelText;
}