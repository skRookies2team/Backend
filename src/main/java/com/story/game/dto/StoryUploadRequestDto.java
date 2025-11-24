package com.story.game.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryUploadRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Novel text is required")
    private String novelText;
}
