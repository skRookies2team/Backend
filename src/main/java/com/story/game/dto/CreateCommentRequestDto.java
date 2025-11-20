package com.story.game.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateCommentRequestDto {

    @NotBlank(message = "Content is required")
    private String content;

    private Long parentId; // 대댓글인 경우 부모 댓글 ID
}
