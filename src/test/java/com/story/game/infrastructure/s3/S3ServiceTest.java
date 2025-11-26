package com.story.game.infrastructure.s3;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3Service 통합 테스트
 *
 * 실행 전 필수:
 * 1. application-dev.yml에 실제 AWS 자격증명 입력
 * 2. S3 버킷 생성 완료
 *
 * 주의: 실제 AWS S3에 연결하므로 약간의 비용이 발생할 수 있습니다.
 */
@SpringBootTest
@ActiveProfiles("dev")
class S3ServiceTest {

    @Autowired
    private S3Service s3Service;

    @Test
    void testUploadAndDownloadFile() {
        // Given
        String testFileKey = "test/sample-" + System.currentTimeMillis() + ".txt";
        String testContent = "안녕하세요! S3 테스트입니다.";

        // When: 파일 업로드
        String uploadedKey = s3Service.uploadFile(testFileKey, testContent);

        // Then: 업로드 성공 확인
        assertThat(uploadedKey).isEqualTo(testFileKey);
        assertThat(s3Service.fileExists(testFileKey)).isTrue();

        // When: 파일 다운로드
        String downloadedContent = s3Service.downloadFileContent(testFileKey);

        // Then: 내용 일치 확인
        assertThat(downloadedContent).isEqualTo(testContent);

        // Cleanup: 테스트 파일 삭제
        s3Service.deleteFile(testFileKey);
        assertThat(s3Service.fileExists(testFileKey)).isFalse();
    }

    @Test
    void testGeneratePresignedUploadUrl() {
        // Given
        String fileKey = "uploads/test-file.txt";

        // When
        S3Service.PresignedUrlInfo urlInfo = s3Service.generatePresignedUploadUrl(fileKey);

        // Then
        assertThat(urlInfo).isNotNull();
        assertThat(urlInfo.getUrl()).isNotEmpty();
        assertThat(urlInfo.getUrl()).contains(fileKey);
        assertThat(urlInfo.getFileKey()).isEqualTo(fileKey);
        assertThat(urlInfo.getExpiresIn()).isEqualTo(900); // 15분
    }

    @Test
    void testGeneratePresignedDownloadUrl() {
        // Given
        String testFileKey = "test/download-test-" + System.currentTimeMillis() + ".txt";
        s3Service.uploadFile(testFileKey, "다운로드 테스트");

        // When
        String downloadUrl = s3Service.generatePresignedDownloadUrl(testFileKey);

        // Then
        assertThat(downloadUrl).isNotEmpty();
        assertThat(downloadUrl).contains(testFileKey);

        // Cleanup
        s3Service.deleteFile(testFileKey);
    }

    @Test
    void testUploadBinaryFile() {
        // Given
        String fileKey = "images/test-image-" + System.currentTimeMillis() + ".jpg";
        byte[] imageData = "fake-image-data".getBytes();
        String contentType = "image/jpeg";

        // When
        String uploadedKey = s3Service.uploadBinaryFile(fileKey, imageData, contentType);

        // Then
        assertThat(uploadedKey).isEqualTo(fileKey);
        assertThat(s3Service.fileExists(fileKey)).isTrue();

        // Cleanup
        s3Service.deleteFile(fileKey);
    }
}
