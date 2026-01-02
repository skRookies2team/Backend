package com.story.game.auth.service;

import com.story.game.auth.dto.LoginRequestDto;
import com.story.game.auth.dto.SignUpRequestDto;
import com.story.game.auth.dto.TokenResponseDto;
import com.story.game.auth.entity.RefreshToken;
import com.story.game.auth.entity.User;
import com.story.game.auth.repository.RefreshTokenRepository;
import com.story.game.auth.repository.UserRepository;
import com.story.game.auth.security.JwtTokenProvider;
import com.story.game.common.exception.DuplicateResourceException;
import com.story.game.common.exception.InvalidInputException;
import com.story.game.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
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
        log.info("=== Sign Up Request ===");
        log.info("Username: {}, Email: {}", request.getUsername(), request.getEmail());

        // 중복 체크
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Username already exists: {}", request.getUsername());
            throw new DuplicateResourceException("User", "username", request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new DuplicateResourceException("User", "email", request.getEmail());
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
        log.info("✅ User created successfully: {}", user.getUsername());

        // 토큰 생성
        return createTokenResponse(user);
    }

    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {
        log.info("=== Login Request ===");
        log.info("Username: {}", request.getUsername());

        // 인증
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = (User) authentication.getPrincipal();
        log.info("✅ Authentication successful for user: {}", user.getUsername());

        // 토큰 생성
        return createTokenResponse(user);
    }

    @Transactional
    public TokenResponseDto refreshToken(String refreshToken) {
        log.info("=== Refresh Token Request ===");

        // 1. 토큰 형식 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            log.warn("Invalid refresh token format");
            throw new InvalidInputException("Invalid refresh token format");
        }

        // 2. JWT에서 사용자명 추출
        String username = jwtTokenProvider.getUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found for refresh token: {}", username);
                    return new ResourceNotFoundException("User not found with username: " + username);
                });

        // 3. DB에서 해당 사용자의 리프레시 토큰 조회
        RefreshToken storedToken = refreshTokenRepository.findByUser(user)
                .orElseThrow(() -> {
                    log.warn("Refresh token not found in database for user: {}", username);
                    return new InvalidInputException("Refresh token not found or expired");
                });

        // 4. 토큰 해시 비교
        if (!passwordEncoder.matches(refreshToken, storedToken.getToken())) {
            log.warn("Refresh token mismatch for user: {}", username);
            throw new InvalidInputException("Invalid refresh token");
        }

        log.info("Refresh token verified for user: {}", user.getUsername());

        // 5. 만료 확인
        if (storedToken.isExpired()) {
            log.warn("Refresh token expired for user: {}", user.getUsername());
            refreshTokenRepository.delete(storedToken);
            throw new InvalidInputException("Refresh token expired");
        }

        // 6. 새 액세스 토큰만 생성 (리프레시 토큰은 유지!)
        String newAccessToken = jwtTokenProvider.createAccessToken(user.getUsername());

        // 7. 리프레시 토큰 마지막 사용 시간 업데이트
        storedToken.updateLastUsedAt();
        refreshTokenRepository.save(storedToken);

        log.info("✅ Access token refreshed successfully for user: {}", user.getUsername());
        log.info("Refresh token remains valid until: {}", storedToken.getExpiryDate());

        return TokenResponseDto.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)  // 기존 리프레시 토큰 그대로 반환
                .tokenType("Bearer")
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }

    @Transactional
    public void logout(String username) {
        log.info("=== Logout Request ===");
        log.info("Username: {}", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("User not found for logout: {}", username);
                    return new ResourceNotFoundException("User not found with username: " + username);
                });

        refreshTokenRepository.deleteByUser(user);
        log.info("✅ Logout successful for user: {}", username);
    }

    private TokenResponseDto createTokenResponse(User user) {
        log.info("Creating token response for user: {}", user.getUsername());

        String accessToken = jwtTokenProvider.createAccessToken(user.getUsername());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUsername());

        // 기존 리프레시 토큰 삭제 (새 로그인 시)
        refreshTokenRepository.deleteByUser(user);

        // 리프레시 토큰을 해시화하여 저장
        LocalDateTime expiryDate = LocalDateTime.now()
                .plusSeconds(jwtTokenProvider.getRefreshTokenValidity() / 1000);

        RefreshToken tokenEntity = RefreshToken.builder()
                .user(user)
                .token(passwordEncoder.encode(refreshToken))  // BCrypt 해시 저장
                .expiryDate(expiryDate)
                .build();

        refreshTokenRepository.save(tokenEntity);
        log.info("✅ Refresh token saved (hashed). Valid until: {}", expiryDate);

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
