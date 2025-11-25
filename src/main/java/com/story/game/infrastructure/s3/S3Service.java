package com.story.game.infrastructure.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class S3Service {

    private final AmazonS3 amazonS3;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    /**
     * Pre-signed URL 생성 (업로드용)
     */
    public PresignedUrlInfo generatePresignedUploadUrl(String fileKey) {
        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * 15; // 15분 유효
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, fileKey)
                .withMethod(HttpMethod.PUT)
                .withExpiration(expiration);

        URL url = amazonS3.generatePresignedUrl(request);

        log.info("Generated pre-signed upload URL for file: {}", fileKey);

        return PresignedUrlInfo.builder()
                .url(url.toString())
                .fileKey(fileKey)
                .expiresIn(900) // 15분
                .build();
    }

    /**
     * Pre-signed URL 생성 (다운로드용)
     */
    public String generatePresignedDownloadUrl(String fileKey) {
        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * 60; // 1시간 유효
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, fileKey)
                .withMethod(HttpMethod.GET)
                .withExpiration(expiration);

        URL url = amazonS3.generatePresignedUrl(request);

        log.info("Generated pre-signed download URL for file: {}", fileKey);

        return url.toString();
    }

    /**
     * S3에서 파일 내용 읽기 (텍스트)
     */
    public String downloadFileContent(String fileKey) {
        try {
            log.info("Downloading file from S3: {}", fileKey);

            S3Object s3Object = amazonS3.getObject(bucketName, fileKey);

            try (InputStream inputStream = s3Object.getObjectContent()) {
                String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                log.info("File downloaded successfully: {} ({} bytes)", fileKey, content.length());
                return content;
            }
        } catch (Exception e) {
            log.error("Failed to download file from S3: {}", fileKey, e);
            throw new RuntimeException("Failed to download file from S3: " + e.getMessage());
        }
    }

    /**
     * S3에 파일 업로드 (서버에서 직접 - 텍스트)
     */
    public String uploadFile(String fileKey, String content) {
        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(contentBytes);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentBytes.length);
            metadata.setContentType("text/plain");

            amazonS3.putObject(bucketName, fileKey, inputStream, metadata);

            log.info("File uploaded to S3: {}", fileKey);

            return fileKey;
        } catch (Exception e) {
            log.error("Failed to upload file to S3", e);
            throw new RuntimeException("Failed to upload file to S3: " + e.getMessage());
        }
    }

    /**
     * S3에 바이너리 파일 업로드 (이미지 등)
     */
    public String uploadBinaryFile(String fileKey, byte[] content, String contentType) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content);

            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setContentType(contentType);

            amazonS3.putObject(bucketName, fileKey, inputStream, metadata);

            log.info("Binary file uploaded to S3: {} ({} bytes, type: {})", fileKey, content.length, contentType);

            return fileKey;
        } catch (Exception e) {
            log.error("Failed to upload binary file to S3: {}", fileKey, e);
            throw new RuntimeException("Failed to upload binary file to S3: " + e.getMessage());
        }
    }

    /**
     * S3에서 파일 삭제
     */
    public void deleteFile(String fileKey) {
        try {
            amazonS3.deleteObject(bucketName, fileKey);
            log.info("File deleted from S3: {}", fileKey);
        } catch (Exception e) {
            log.error("Failed to delete file from S3: {}", fileKey, e);
            throw new RuntimeException("Failed to delete file from S3: " + e.getMessage());
        }
    }

    /**
     * 파일 존재 여부 확인
     */
    public boolean fileExists(String fileKey) {
        return amazonS3.doesObjectExist(bucketName, fileKey);
    }

    /**
     * Pre-signed URL 정보 DTO
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PresignedUrlInfo {
        private String url;
        private String fileKey;
        private Integer expiresIn; // seconds
    }
}
