package com.story.game.community.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostMediaUploadResponseDto {
    private Long mediaId;
    private String mediaType;  // IMAGE or VIDEO
    private String mediaUrl;
    private String mediaKey;
    private Integer mediaOrder;
    private Long fileSize;
    private String message;
}
