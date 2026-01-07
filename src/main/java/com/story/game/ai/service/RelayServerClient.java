package com.story.game.ai.service;

import com.story.game.ai.dto.ImageGenerationRequestDto;
import com.story.game.ai.dto.ImageGenerationResponseDto;
import com.story.game.ai.dto.NovelStyleLearnRequestDto;
import com.story.game.ai.dto.NovelStyleLearnResponseDto;
import com.story.game.gameplay.dto.BgmDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelayServerClient {

    @Qualifier("relayServerWebClient")
    private final WebClient relayServerWebClient;

    /**
     * Request novel style learning from relay server
     */
    public NovelStyleLearnResponseDto learnNovelStyle(NovelStyleLearnRequestDto request) {
        log.info("Requesting novel style learning from relay server for story: {}", request.getStory_id());

        try {
            NovelStyleLearnResponseDto response = relayServerWebClient.post()
                .uri("/ai/learn-novel-style")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NovelStyleLearnResponseDto.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Relay server error during novel style learning: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.empty();
                })
                .block();

            if (response != null && response.getThumbnail_image_url() != null) {
                log.info("Novel style learning completed with thumbnail: {}", response.getThumbnail_image_url());
            } else {
                log.warn("Novel style learning completed but no thumbnail generated");
            }
            return response;

        } catch (Exception e) {
            log.error("Failed to learn novel style: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Request image generation from relay server
     */
    public ImageGenerationResponseDto generateImage(ImageGenerationRequestDto request) {
        log.info("Requesting image generation from relay server for node: {}", request.getNodeText());

        try {
            ImageGenerationResponseDto response = relayServerWebClient.post()
                .uri("/ai-image/api/v1/generate-image")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ImageGenerationResponseDto.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Relay server error during image generation: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("Image generation failed: " + e.getMessage()));
                })
                .block();

            if (response == null) {
                throw new RuntimeException("No response from relay server");
            }

            log.info("Image generated successfully: {}", response.getImageUrl());
            return response;

        } catch (Exception e) {
            log.error("Failed to generate image: {}", e.getMessage(), e);
            throw new RuntimeException("Image generation failed", e);
        }
    }

    /**
     * Request music recommendation from relay server
     */
    public BgmDto recommendMusic(String prompt) {
        log.info("Requesting music recommendation from relay server");
        log.debug("Prompt: {}", prompt.length() > 100 ? prompt.substring(0, 100) + "..." : prompt);

        try {
            Map<String, String> request = new HashMap<>();
            request.put("prompt", prompt);

            Map<String, Object> response = relayServerWebClient.post()
                .uri("/ai/recommend-music")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Relay server error during music recommendation: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.empty();
                })
                .block();

            if (response == null) {
                log.warn("No response from relay server for music recommendation");
                return null;
            }

            // Parse response
            Map<String, Object> analysisData = (Map<String, Object>) response.get("analysis");
            Map<String, Object> musicData = (Map<String, Object>) response.get("music");

            if (musicData == null) {
                log.warn("No music data in response");
                return null;
            }

            BgmDto bgm = BgmDto.builder()
                .mood((String) musicData.get("mood"))
                .filename((String) musicData.get("filename"))
                .streamingUrl((String) musicData.get("streaming_url"))
                .intensity(analysisData != null ? (Double) analysisData.get("intensity") : null)
                .emotionalTags(analysisData != null ? (List<String>) analysisData.get("emotional_tags") : null)
                .build();

            log.info("Music recommended: mood={}, file={}", bgm.getMood(), bgm.getFilename());
            return bgm;

        } catch (Exception e) {
            log.error("Failed to recommend music: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Check relay server health
     */
    public boolean checkHealth() {
        try {
            String response = relayServerWebClient.get()
                .uri("/ai/health")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(5))
                .block();
            return response != null;
        } catch (Exception e) {
            log.warn("Relay server health check failed: {}", e.getMessage());
            return false;
        }
    }
}
