package com.story.game.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCommentRequestDto {

    @NotBlank(message = "Content is required")
    @Size(max = 1000, message = "Content must be less than 1000 characters")
    private String content;
}
