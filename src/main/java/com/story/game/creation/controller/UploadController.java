package com.story.game.creation.controller;

import com.story.game.creation.dto.PresignedUrlResponseDto;
import com.story.game.infrastructure.s3.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
@Tag(name = "File Upload", description = "S3를 이용한 파일 업로드 API")
public class UploadController {

    private final S3Service s3Service;

    /**
     * Pre-signed URL 요청 (파일 업로드용)
     */
    @GetMapping("/presigned-url")
    @Operation(
            summary = "Pre-signed URL 생성",
            description = "S3에 파일을 직접 업로드하기 위한 Pre-signed URL을 생성합니다. " +
                    "프론트엔드는 이 URL을 사용하여 S3에 직접 파일을 업로드할 수 있습니다. " +
                    "URL은 15분간 유효합니다."
    )
    public ResponseEntity<PresignedUrlResponseDto> getPresignedUrl(
            @RequestParam String fileName) {
        log.info("=== Generate Pre-signed URL Request ===");
        log.info("FileName: {}", fileName);

        S3Service.PresignedUrlInfo urlInfo = s3Service.generatePresignedUploadUrl(fileName);

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

        String downloadUrl = s3Service.generatePresignedDownloadUrl(fileKey);

        log.info("Download URL generated for: {}", fileKey);
        return ResponseEntity.ok(downloadUrl);
    }
}
