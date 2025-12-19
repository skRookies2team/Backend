package com.story.game.creation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeImageResponseDto {
    private String nodeId;
    private String imageUrl;        // Presigned download URL
    private String imageFileKey;
}
