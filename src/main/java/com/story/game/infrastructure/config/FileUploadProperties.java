package com.story.game.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 파일 업로드 관련 설정
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.file-upload")
public class FileUploadProperties {

    /**
     * 파일 크기 제한 (bytes)
     */
    private Size maxSize = new Size();

    /**
     * 허용된 파일 확장자
     */
    private Extensions allowedExtensions = new Extensions();

    /**
     * Pre-signed URL 유효 시간 (초)
     */
    private int presignedUrlExpiration = 900; // 15분

    @Data
    public static class Size {
        private long story = 10 * 1024 * 1024;   // 10MB
        private long image = 5 * 1024 * 1024;    // 5MB
        private long video = 100 * 1024 * 1024;  // 100MB
    }

    @Data
    public static class Extensions {
        private Set<String> story = Set.of("txt", "pdf", "doc", "docx");
        private Set<String> image = Set.of("jpg", "jpeg", "png", "gif", "webp");
        private Set<String> video = Set.of("mp4", "avi", "mov", "wmv", "flv", "mkv");
    }
}
