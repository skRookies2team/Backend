package com.story.game.service;

import com.story.game.dto.LoginRequestDto;
import com.story.game.dto.SignUpRequestDto;
import com.story.game.dto.TokenResponseDto;
import com.story.game.entity.RefreshToken;
import com.story.game.entity.User;
import com.story.game.repository.RefreshTokenRepository;
import com.story.game.repository.UserRepository;
import com.story.game.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public TokenResponseDto signUp(SignUpRequestDto request) {
        // 중복 체크
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        // 사용자 생성
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .role(User.Role.USER)
                .build();

        userRepository.save(user);

        // 토큰 생성
        return createTokenResponse(user);
    }

    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {
        // 인증
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = (User) authentication.getPrincipal();

        // 토큰 생성
        return createTokenResponse(user);
    }

    @Transactional
    public TokenResponseDto refreshToken(String refreshToken) {
        // 토큰 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        // DB에서 토큰 확인
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new IllegalArgumentException("Refresh token expired");
        }

        // 새 토큰 생성
        User user = storedToken.getUser();
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getUsername());
        String newRefreshToken = jwtTokenProvider.createRefreshToken(user.getUsername());

        // 리프레시 토큰 업데이트
        LocalDateTime newExpiryDate = LocalDateTime.now()
                .plusSeconds(jwtTokenProvider.getRefreshTokenValidity() / 1000);
        storedToken.updateToken(newRefreshToken, newExpiryDate);
        refreshTokenRepository.save(storedToken);

        return TokenResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    @Transactional
    public void logout(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        refreshTokenRepository.deleteByUser(user);
    }

    private TokenResponseDto createTokenResponse(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getUsername());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUsername());

        // 리프레시 토큰 저장
        LocalDateTime expiryDate = LocalDateTime.now()
                .plusSeconds(jwtTokenProvider.getRefreshTokenValidity() / 1000);

        RefreshToken tokenEntity = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiryDate(expiryDate)
                .build();

        refreshTokenRepository.save(tokenEntity);

        return TokenResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
}
