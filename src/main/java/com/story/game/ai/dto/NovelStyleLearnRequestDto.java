package com.story.game.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for learning novel style in AI-IMAGE server
 * Maps to relay-server's /ai/learn-novel-style endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovelStyleLearnRequestDto {

    @NotBlank(message = "Story ID is required")
    @JsonProperty("story_id")
    private String story_id;

    // 소설 텍스트 제공 방식 (다음 중 하나)
    // 1. novel_text 직접 제공
    // 2. novel_s3_bucket + novel_s3_key로 S3에서 다운로드
    @JsonProperty("novel_text")
    private String novel_text;

    @JsonProperty("title")
    private String title;

    // S3 소설 텍스트 위치 (선택)
    @JsonProperty("novel_s3_bucket")
    private String novel_s3_bucket;

    @JsonProperty("novel_s3_key")
    private String novel_s3_key;

    // 썸네일 이미지 S3 업로드 정보 (선택)
    @JsonProperty("thumbnail_s3_url")
    private String thumbnail_s3_url;        // Presigned Upload URL

    @JsonProperty("thumbnail_s3_bucket")
    private String thumbnail_s3_bucket;

    @JsonProperty("thumbnail_s3_key")
    private String thumbnail_s3_key;
}
