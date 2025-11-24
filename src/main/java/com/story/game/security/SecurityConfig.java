package com.story.game.security;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 인증 관련 - 모두 허용
                        .requestMatchers("/api/auth/**").permitAll()

                        // 게임 스토리 조회 - 로그인 없이 가능
                        .requestMatchers(HttpMethod.GET, "/api/game/stories/**").permitAll()
                        .requestMatchers("/api/game/ai/health").permitAll()
                        .requestMatchers("/api/game/stories/analyze").permitAll()

                        // 커뮤니티 조회 (GET) - 로그인 없이 가능
                        .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/comments/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()

                        // 댓글 작성/수정/삭제/좋아요 - 로그인 필요 (이미 anyRequest().authenticated()로 처리됨)

                        // Swagger
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**",
                                       "/swagger-resources/**", "/webjars/**").permitAll()

                        // 그 외 모든 요청은 인증 필요 (작성, 수정, 삭제 등)
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            System.out.println("=== Authentication Failed ===");
                            System.out.println("Request: " + request.getMethod() + " " + request.getRequestURI());
                            System.out.println("Reason: " + authException.getMessage());
                            System.out.println("==============================");
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            System.out.println("=== Access Denied (403) ===");
                            System.out.println("Request: " + request.getMethod() + " " + request.getRequestURI());
                            System.out.println("Reason: " + accessDeniedException.getMessage());
                            System.out.println("User: " + request.getUserPrincipal());
                            System.out.println("===========================");
                            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
