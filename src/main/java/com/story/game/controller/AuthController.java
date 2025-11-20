package com.story.game.controller;

import com.story.game.dto.LoginRequestDto;
import com.story.game.dto.RefreshTokenRequestDto;
import com.story.game.dto.SignUpRequestDto;
import com.story.game.dto.TokenResponseDto;
import com.story.game.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다")
    public ResponseEntity<TokenResponseDto> signUp(@Valid @RequestBody SignUpRequestDto request) {
        return ResponseEntity.ok(authService.signUp(request));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "사용자 로그인을 처리합니다")
    public ResponseEntity<TokenResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        return ResponseEntity.ok(authService.login(request));
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
