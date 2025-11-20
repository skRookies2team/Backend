package com.story.game.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinalEndingDto {
    private String id;
    private String type;
    private String title;
    private String condition;
    private String summary;
}
