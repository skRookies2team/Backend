package com.story.game.user.controller;

import com.story.game.achievement.dto.AchievementDto;
import com.story.game.user.dto.GameHistoryDto;
import com.story.game.user.dto.ProfileImageUploadResponseDto;
import com.story.game.user.dto.UpdateProfileRequestDto;
import com.story.game.user.dto.UserProfileDto;
import com.story.game.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "마이페이지 관련 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "내 프로필 조회", description = "현재 로그인한 사용자의 프로필을 조회합니다")
    public ResponseEntity<UserProfileDto> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserProfile(userDetails.getUsername()));
    }

    @PutMapping("/me")
    @Operation(summary = "프로필 수정", description = "사용자 프로필을 수정합니다")
    public ResponseEntity<UserProfileDto> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequestDto request) {
        return ResponseEntity.ok(userService.updateProfile(userDetails.getUsername(), request));
    }

    @PostMapping("/me/profile-image")
    @Operation(summary = "프로필 이미지 업로드", description = "프로필 이미지를 S3에 업로드하고 URL을 반환합니다")
    public ResponseEntity<ProfileImageUploadResponseDto> uploadProfileImage(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam("profileImage") MultipartFile imageFile) {
        return ResponseEntity.ok(userService.uploadProfileImage(userDetails.getUsername(), imageFile));
    }

    @GetMapping("/me/history")
    @Operation(summary = "게임 플레이 기록 조회", description = "플레이한 스토리 목록과 엔딩 달성 현황을 조회합니다")
    public ResponseEntity<List<GameHistoryDto>> getGameHistory(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getGameHistory(userDetails.getUsername()));
    }

    @GetMapping("/me/achievements")
    @Operation(summary = "업적 조회", description = "사용자의 업적 달성 현황을 조회합니다")
    public ResponseEntity<List<AchievementDto>> getAchievements(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserAchievements(userDetails.getUsername()));
    }

    @GetMapping("/{username}")
    @Operation(summary = "다른 사용자 프로필 조회", description = "특정 사용자의 공개 프로필을 조회합니다")
    public ResponseEntity<UserProfileDto> getUserProfile(@PathVariable String username) {
        return ResponseEntity.ok(userService.getUserProfile(username));
    }
}
