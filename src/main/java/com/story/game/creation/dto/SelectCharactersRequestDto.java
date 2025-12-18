package com.story.game.creation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 챗봇용 캐릭터 선택 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectCharactersRequestDto {

    @NotEmpty(message = "At least one character must be selected")
    @Size(min = 1, max = 2, message = "1-2 characters must be selected")
    private List<String> characterNames;
}
