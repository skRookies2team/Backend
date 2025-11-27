package com.story.game.community.dto;

import com.story.game.community.entity.PostMedia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaDto {
    private Long mediaId;
    private String mediaType;  // IMAGE or VIDEO
    private String mediaUrl;
    private Integer mediaOrder;

    public static MediaDto from(PostMedia media) {
        return MediaDto.builder()
                .mediaId(media.getId())
                .mediaType(media.getMediaType().name())
                .mediaUrl(media.getMediaUrl())
                .mediaOrder(media.getMediaOrder())
                .build();
    }
}
