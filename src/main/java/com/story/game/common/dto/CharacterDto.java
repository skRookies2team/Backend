package com.story.game.common.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterDto {
    private String name;
    private List<String> aliases;
    private String description;
    private List<String> relationships;

    /**
     * NPC 대화용 Character ID (캐릭터별 고유 ID)
     * 형식: "{storyId}_{characterName}" (예: "story_abc12345_홍길동")
     * 프론트엔드에서 POST /api/rag/chat 호출 시 이 값을 사용
     */
    private String chatCharacterId;
}
