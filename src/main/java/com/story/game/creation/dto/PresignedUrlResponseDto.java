package com.story.game.creation.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PresignedUrlResponseDto {

    private String uploadUrl;
    private String fileKey;
    private Integer expiresIn; // seconds
    private String method; // PUT
}
