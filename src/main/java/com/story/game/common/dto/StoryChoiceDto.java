package com.story.game.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryChoiceDto {
    private String text;
    private List<String> tags;

    @JsonAlias("immediate_reaction")  // Python AI에서 받을 때만 snake_case 허용
    private String immediateReaction;  // 프론트로 보낼 때는 camelCase 사용
}
