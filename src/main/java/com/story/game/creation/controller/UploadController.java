package com.story.game.creation.controller;

import com.story.game.creation.dto.PresignedUrlResponseDto;
import com.story.game.infrastructure.config.FileUploadProperties;
import com.story.game.infrastructure.s3.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "File Upload", description = "S3를 이용한 파일 업로드 API")
public class UploadController {

    private final S3Service s3Service;
    private final FileUploadProperties uploadProperties;

    /**
     * Pre-signed URL 요청 (파일 업로드용 - 범용)
     * @deprecated 타입별 엔드포인트 사용 권장
     */
    @Deprecated
    @GetMapping("/presigned-url")
    @Operation(
            summary = "Pre-signed URL 생성 (범용)",
            description = "S3에 파일을 직접 업로드하기 위한 Pre-signed URL을 생성합니다. " +
                    "타입별 엔드포인트 사용을 권장합니다 (/story, /image, /video). " +
                    "fileKey는 'uploads/{type}/{UUID}_{originalFileName}' 형식으로 자동 생성됩니다."
    )
    public ResponseEntity<PresignedUrlResponseDto> getPresignedUrl(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "general") String fileType) {
        log.info("=== Generate Pre-signed URL Request (Generic) ===");
        log.info("FileName: {}, FileType: {}", fileName, fileType);

        String uniqueFileKey = generateFileKey(fileType, fileName);
        return createPresignedUrlResponse(uniqueFileKey);
    }

    /**
     * 스토리(소설) 파일 업로드용 Pre-signed URL
     */
    @GetMapping("/story/presigned-url")
    @Operation(
            summary = "스토리 파일 업로드용 Pre-signed URL",
            description = "소설 텍스트 파일(.txt, .pdf, .doc, .docx)을 업로드하기 위한 Pre-signed URL을 생성합니다. " +
                    "파일은 'uploads/stories/{UUID}_{fileName}' 경로에 저장됩니다. " +
                    "최대 파일 크기: 10MB"
    )
    public ResponseEntity<PresignedUrlResponseDto> getStoryPresignedUrl(
            @RequestParam String fileName,
            @RequestParam(required = false) Long fileSize) {
        log.info("=== Generate Story Upload URL ===");
        log.info("Story FileName: {}, FileSize: {}", fileName, fileSize);

        // 파일 확장자 검증
        validateFileExtension(fileName, uploadProperties.getAllowedExtensions().getStory(), "스토리");

        // 파일 크기 검증 (선택사항)
        if (fileSize != null) {
            validateFileSize(fileSize, uploadProperties.getMaxSize().getStory(), "스토리");
        }

        String uniqueFileKey = generateFileKey("stories", fileName);
        return createPresignedUrlResponse(uniqueFileKey);
    }

    /**
     * 이미지 파일 업로드용 Pre-signed URL
     */
    @GetMapping("/image/presigned-url")
    @Operation(
            summary = "이미지 파일 업로드용 Pre-signed URL",
            description = "이미지 파일(.jpg, .jpeg, .png, .gif, .webp)을 업로드하기 위한 Pre-signed URL을 생성합니다. " +
                    "파일은 'uploads/images/{UUID}_{fileName}' 경로에 저장됩니다. " +
                    "최대 파일 크기: 5MB"
    )
    public ResponseEntity<PresignedUrlResponseDto> getImagePresignedUrl(
            @RequestParam String fileName,
            @RequestParam(required = false) Long fileSize) {
        log.info("=== Generate Image Upload URL ===");
        log.info("Image FileName: {}, FileSize: {}", fileName, fileSize);

        // 파일 확장자 검증
        validateFileExtension(fileName, uploadProperties.getAllowedExtensions().getImage(), "이미지");

        // 파일 크기 검증 (선택사항)
        if (fileSize != null) {
            validateFileSize(fileSize, uploadProperties.getMaxSize().getImage(), "이미지");
        }

        String uniqueFileKey = generateFileKey("images", fileName);
        return createPresignedUrlResponse(uniqueFileKey);
    }

    /**
     * 동영상 파일 업로드용 Pre-signed URL
     */
    @GetMapping("/video/presigned-url")
    @Operation(
            summary = "동영상 파일 업로드용 Pre-signed URL",
            description = "동영상 파일(.mp4, .avi, .mov, .wmv, .flv, .mkv)을 업로드하기 위한 Pre-signed URL을 생성합니다. " +
                    "파일은 'uploads/videos/{UUID}_{fileName}' 경로에 저장됩니다. " +
                    "최대 파일 크기: 100MB"
    )
    public ResponseEntity<PresignedUrlResponseDto> getVideoPresignedUrl(
            @RequestParam String fileName,
            @RequestParam(required = false) Long fileSize) {
        log.info("=== Generate Video Upload URL ===");
        log.info("Video FileName: {}, FileSize: {}", fileName, fileSize);

        // 파일 확장자 검증
        validateFileExtension(fileName, uploadProperties.getAllowedExtensions().getVideo(), "동영상");

        // 파일 크기 검증 (선택사항)
        if (fileSize != null) {
            validateFileSize(fileSize, uploadProperties.getMaxSize().getVideo(), "동영상");
        }

        String uniqueFileKey = generateFileKey("videos", fileName);
        return createPresignedUrlResponse(uniqueFileKey);
    }

    /**
     * 고유한 fileKey 생성 헬퍼 메서드
     */
    private String generateFileKey(String fileType, String fileName) {
        return String.format("uploads/%s/%s_%s",
                fileType,
                UUID.randomUUID().toString(),
                fileName);
    }

    /**
     * Pre-signed URL 응답 생성 헬퍼 메서드
     */
    private ResponseEntity<PresignedUrlResponseDto> createPresignedUrlResponse(String fileKey) {
        S3Service.PresignedUrlInfo urlInfo = s3Service.generatePresignedUploadUrl(fileKey);

        PresignedUrlResponseDto response = PresignedUrlResponseDto.builder()
                .uploadUrl(urlInfo.getUrl())
                .fileKey(urlInfo.getFileKey())
                .expiresIn(urlInfo.getExpiresIn())
                .method("PUT")
                .build();

        log.info("Pre-signed URL generated. FileKey: {}", response.getFileKey());
        return ResponseEntity.ok(response);
    }

    /**
     * 다운로드용 Pre-signed URL 생성
     */
    @GetMapping("/download-url")
    @Operation(
            summary = "다운로드용 Pre-signed URL 생성",
            description = "S3에 저장된 파일을 다운로드하기 위한 Pre-signed URL을 생성합니다. " +
                    "URL은 1시간 동안 유효합니다."
    )
    public ResponseEntity<String> getDownloadUrl(
            @RequestParam String fileKey) {
        log.info("=== Generate Download URL Request ===");
        log.info("FileKey: {}", fileKey);

        // Path Traversal 방지: 안전한 경로만 허용
        validateFileKey(fileKey);

        String downloadUrl = s3Service.generatePresignedDownloadUrl(fileKey);

        log.info("Download URL generated for: {}", fileKey);
        return ResponseEntity.ok(downloadUrl);
    }

    /**
     * 파일 경로 검증 (Path Traversal 방지)
     */
    private void validateFileKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("File key cannot be empty");
        }

        // ".." 경로 탐색 방지
        if (fileKey.contains("..")) {
            throw new IllegalArgumentException("Invalid file path: path traversal detected");
        }

        // 절대 경로 방지
        if (fileKey.startsWith("/") || fileKey.startsWith("\\")) {
            throw new IllegalArgumentException("Invalid file path: absolute path not allowed");
        }

        // 허용된 경로 패턴만 통과
        // uploads/, novels/, images/, story-images/, profile-images/ 등
        if (!fileKey.matches("^(uploads|novels|images|story-images|profile-images|post-media)/.*")) {
            throw new IllegalArgumentException("Invalid file path: unauthorized directory");
        }
    }

    /**
     * 파일 업로드 완료 확인 API
     * S3 업로드 후 파일이 실제로 존재하는지 검증
     */
    @PostMapping("/verify")
    @Operation(
            summary = "파일 업로드 완료 확인",
            description = "S3에 파일이 정상적으로 업로드되었는지 확인합니다."
    )
    public ResponseEntity<Map<String, Object>> verifyUpload(@RequestParam String fileKey) {
        log.info("=== Verify Upload Request ===");
        log.info("FileKey: {}", fileKey);

        boolean exists = s3Service.fileExists(fileKey);

        Map<String, Object> response = new HashMap<>();
        response.put("fileKey", fileKey);
        response.put("exists", exists);
        response.put("verified", exists);

        if (exists) {
            log.info("File verified successfully: {}", fileKey);
        } else {
            log.warn("File not found in S3: {}", fileKey);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 파일 삭제 API
     * 업로드 실패 또는 취소 시 S3 파일 정리
     */
    @DeleteMapping("/file")
    @Operation(
            summary = "파일 삭제",
            description = "S3에 업로드된 파일을 삭제합니다. 업로드 실패 시 정리용으로 사용합니다."
    )
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestParam String fileKey) {
        log.info("=== Delete File Request ===");
        log.info("FileKey: {}", fileKey);

        try {
            s3Service.deleteFile(fileKey);

            Map<String, Object> response = new HashMap<>();
            response.put("fileKey", fileKey);
            response.put("deleted", true);
            response.put("message", "File deleted successfully");

            log.info("File deleted: {}", fileKey);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to delete file: {}", fileKey, e);

            Map<String, Object> response = new HashMap<>();
            response.put("fileKey", fileKey);
            response.put("deleted", false);
            response.put("error", e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    // ===== 검증 메서드 =====

    /**
     * 파일 확장자 검증
     */
    private void validateFileExtension(String fileName, Set<String> allowedExtensions, String fileType) {
        String extension = getFileExtension(fileName);

        if (!allowedExtensions.contains(extension)) {
            String allowed = String.join(", ", allowedExtensions);
            String message = String.format(
                    "%s 파일은 다음 확장자만 허용됩니다: %s (입력: %s)",
                    fileType, allowed, extension
            );
            log.warn("File extension validation failed: {}", message);
            throw new IllegalArgumentException(message);
        }

        log.info("File extension validated: {} ({})", extension, fileType);
    }

    /**
     * 파일 크기 검증
     */
    private void validateFileSize(long fileSize, long maxSize, String fileType) {
        if (fileSize > maxSize) {
            String message = String.format(
                    "%s 파일 크기는 %dMB를 초과할 수 없습니다. (입력: %.2fMB)",
                    fileType,
                    maxSize / 1024 / 1024,
                    fileSize / 1024.0 / 1024.0
            );
            log.warn("File size validation failed: {}", message);
            throw new IllegalArgumentException(message);
        }

        log.info("File size validated: {:.2f}MB (max: {}MB)",
                fileSize / 1024.0 / 1024.0,
                maxSize / 1024 / 1024);
    }

    /**
     * 파일 확장자 추출
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new IllegalArgumentException("파일 확장자가 없습니다: " + fileName);
        }

        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
}
