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
     * StoryCreation ID (예: "story_abc12345")
     * 스토리 생성 시 사용되는 ID
     */
    private String storyId;

    /**
     * StoryData ID (예: 1, 2, 3)
     * 게임 플레이 시 사용되는 ID (nullable - 스토리 생성 완료 전에는 null)
     */
    private Long storyDataId;

    /**
     * NPC 대화용 Character ID (실제로는 storyId와 동일)
     * 프론트엔드에서 POST /api/rag/chat 호출 시 이 값을 characterId로 사용해야 함
     */
    private String chatCharacterId;

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
