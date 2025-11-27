package com.story.game.creation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.story.game.common.dto.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NovelAnalysisResponseDto {
    // 레거시 방식: 직접 반환
    private String summary;
    private List<CharacterDto> characters;
    private List<GaugeDto> gauges;

    // 새로운 방식: S3 업로드 후 fileKey만 반환
    @JsonProperty("file_key")
    private String fileKey;  // S3에 업로드된 분석 결과 파일의 키

    // S3 방식인지 확인
    public boolean isS3Mode() {
        return fileKey != null && !fileKey.isBlank();
    }
}