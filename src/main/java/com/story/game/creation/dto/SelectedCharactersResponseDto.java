package com.story.game.creation.dto;

import com.story.game.common.dto.CharacterDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 선택된 캐릭터 조회 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectedCharactersResponseDto {

    /**
     * 선택된 캐릭터 이름 목록
     */
    private List<String> selectedCharacterNames;

    /**
     * 선택된 캐릭터들의 상세 정보
     */
    private List<CharacterDto> selectedCharacters;

    /**
     * 캐릭터 선택 여부
     */
    private boolean hasSelection;
}
