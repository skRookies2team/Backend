package com.story.game.infrastructure.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * S3Service Mock 단위 테스트
 *
 * 실제 AWS 연결 없이 빠르게 테스트
 * CI/CD 파이프라인에 적합
 */
@ExtendWith(MockitoExtension.class)
class S3ServiceMockTest {

    @Mock
    private AmazonS3 amazonS3;

    @InjectMocks
    private S3Service s3Service;

    @BeforeEach
    void setUp() {
        // @Value로 주입되는 필드를 리플렉션으로 설정
        ReflectionTestUtils.setField(s3Service, "bucketName", "test-bucket");
    }

    @Test
    void testUploadFile() {
        // Given
        String fileKey = "test/file.txt";
        String content = "테스트 내용";

        // When
        String result = s3Service.uploadFile(fileKey, content);

        // Then
        assertThat(result).isEqualTo(fileKey);
        verify(amazonS3, times(1)).putObject(
                eq("test-bucket"),
                eq(fileKey),
                any(),
                any()
        );
    }

    @Test
    void testDownloadFileContent() throws Exception {
        // Given
        String fileKey = "test/file.txt";
        String expectedContent = "다운로드된 내용";

        S3Object s3Object = mock(S3Object.class);
        S3ObjectInputStream inputStream = new S3ObjectInputStream(
                new ByteArrayInputStream(expectedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                null
        );

        when(amazonS3.getObject("test-bucket", fileKey)).thenReturn(s3Object);
        when(s3Object.getObjectContent()).thenReturn(inputStream);

        // When
        String result = s3Service.downloadFileContent(fileKey);

        // Then
        assertThat(result).isEqualTo(expectedContent);
        verify(amazonS3, times(1)).getObject("test-bucket", fileKey);
    }

    @Test
    void testFileExists() {
        // Given
        String fileKey = "test/existing-file.txt";
        when(amazonS3.doesObjectExist("test-bucket", fileKey)).thenReturn(true);

        // When
        boolean exists = s3Service.fileExists(fileKey);

        // Then
        assertThat(exists).isTrue();
        verify(amazonS3, times(1)).doesObjectExist("test-bucket", fileKey);
    }

    @Test
    void testDeleteFile() {
        // Given
        String fileKey = "test/file-to-delete.txt";

        // When
        s3Service.deleteFile(fileKey);

        // Then
        verify(amazonS3, times(1)).deleteObject("test-bucket", fileKey);
    }

    @Test
    void testGeneratePresignedUploadUrl() throws Exception {
        // Given
        String fileKey = "uploads/new-file.txt";
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/uploads/new-file.txt?signature=abc");

        when(amazonS3.generatePresignedUrl(any())).thenReturn(mockUrl);

        // When
        S3Service.PresignedUrlInfo result = s3Service.generatePresignedUploadUrl(fileKey);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUrl()).contains(fileKey);
        assertThat(result.getFileKey()).isEqualTo(fileKey);
        assertThat(result.getExpiresIn()).isEqualTo(900);
        verify(amazonS3, times(1)).generatePresignedUrl(any());
    }

    @Test
    void testUploadBinaryFile() {
        // Given
        String fileKey = "images/photo.jpg";
        byte[] content = "fake-image-data".getBytes();
        String contentType = "image/jpeg";

        // When
        String result = s3Service.uploadBinaryFile(fileKey, content, contentType);

        // Then
        assertThat(result).isEqualTo(fileKey);
        verify(amazonS3, times(1)).putObject(
                eq("test-bucket"),
                eq(fileKey),
                any(),
                any()
        );
    }
}
