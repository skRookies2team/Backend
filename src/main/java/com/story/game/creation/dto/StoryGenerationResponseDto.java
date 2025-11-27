package com.story.game.creation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.story.game.common.dto.FullStoryDto;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryGenerationResponseDto {
    private String status;
    private String message;

    // 기존 방식: AI 서버가 전체 JSON 반환 (레거시 지원)
    private FullStoryDto data;

    // 새로운 방식: AI 서버가 S3에 직접 업로드 후 fileKey만 반환
    @JsonProperty("file_key")
    private String fileKey;

    @JsonProperty("metadata")
    private MetadataInfo metadata;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MetadataInfo {
        @JsonProperty("total_episodes")
        private Integer totalEpisodes;

        @JsonProperty("total_nodes")
        private Integer totalNodes;

        @JsonProperty("total_gauges")
        private Integer totalGauges;
    }
}
