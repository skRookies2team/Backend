package com.story.game.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        String method = request.getMethod();

        log.info("=== JWT Filter Start ===");
        log.info("Request: {} {}", method, requestURI);

        String token = resolveToken(request);

        if (token != null) {
            log.info("Token found: {}", token.substring(0, Math.min(20, token.length())) + "...");

            if (jwtTokenProvider.validateToken(token)) {
                log.info("Token is valid");
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.info("Authentication set: {}", authentication.getName());
            } else {
                log.warn("Token is invalid");
            }
        } else {
            log.info("No token found in request");
        }

        log.info("Authentication in context: {}",
            SecurityContextHolder.getContext().getAuthentication() != null ?
            SecurityContextHolder.getContext().getAuthentication().getName() : "anonymous");
        log.info("=== JWT Filter End ===\n");

        filterChain.doFilter(request, response);
    }

    // Request Header에서 Token 추출
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
