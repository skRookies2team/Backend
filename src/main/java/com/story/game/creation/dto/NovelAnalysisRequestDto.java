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

    @JsonProperty("s3_upload_url")
    private String s3UploadUrl;  // AI 서버가 분석 결과를 업로드할 Pre-signed URL

    @JsonProperty("result_file_key")
    private String resultFileKey;  // AI 서버가 업로드할 파일의 키

    @JsonProperty("novel_download_url")
    private String novelDownloadUrl;  // RAG 서버가 원본 소설을 다운로드할 Pre-signed URL

    // S3 방식인지 확인
    public boolean isS3Mode() {
        return fileKey != null && !fileKey.isBlank();
    }
}