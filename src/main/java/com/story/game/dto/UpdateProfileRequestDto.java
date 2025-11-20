package com.story.game.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequestDto {

    @Size(min = 2, max = 50, message = "Nickname must be between 2 and 50 characters")
    private String nickname;

    @Size(max = 500, message = "Bio must be less than 500 characters")
    private String bio;

    private String profileImageUrl;
}
