package com.story.game.creation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NovelAnalysisRequestDto {

    @JsonProperty("novel_text")
    private String novelText;

    @JsonProperty("file_key")
    private String fileKey;

    @JsonProperty("bucket")
    @Builder.Default
    private String bucket = "story-game-bucket";

    // S3 방식인지 확인
    public boolean isS3Mode() {
        return fileKey != null && !fileKey.isBlank();
    }
}