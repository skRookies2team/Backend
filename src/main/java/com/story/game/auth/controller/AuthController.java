package com.story.game.auth.controller;

import com.story.game.auth.dto.LoginRequestDto;
import com.story.game.auth.dto.RefreshTokenRequestDto;
import com.story.game.auth.dto.SignUpRequestDto;
import com.story.game.auth.dto.TokenResponseDto;
import com.story.game.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다")
    public ResponseEntity<TokenResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request) {
        log.info("=== Signup Request ===");
        log.info("Username: {}", request.getUsername());
        log.info("Email: {}", request.getEmail());
        TokenResponseDto response = authService.signUp(request);
        log.info("Signup successful for user: {}", response.getUsername());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 로그인을 처리합니다")
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        log.info("=== Login Request ===");
        log.info("Username: {}", request.getUsername());
        TokenResponseDto response = authService.login(request);
        log.info("Login successful for user: {}", response.getUsername());
        log.info("Access Token: {}...", response.getAccessToken().substring(0, Math.min(20, response.getAccessToken().length())));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신", description = "리프레시 토큰으로 새로운 액세스 토큰을 발급받습니다")
    public ResponseEntity<TokenResponseDto> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        return ResponseEntity.ok(authService.refreshToken(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "사용자 로그아웃을 처리합니다")
    public ResponseEntity<String> logout(@AuthenticationPrincipal UserDetails userDetails) {
        authService.logout(userDetails.getUsername());
        return ResponseEntity.ok("Logout successful");
    }
}
