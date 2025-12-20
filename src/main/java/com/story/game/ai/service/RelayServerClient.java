package com.story.game.ai.service;

import com.story.game.ai.dto.ImageGenerationRequestDto;
import com.story.game.ai.dto.ImageGenerationResponseDto;
import com.story.game.ai.dto.NovelStyleLearnRequestDto;
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
    public Boolean learnNovelStyle(NovelStyleLearnRequestDto request) {
        log.info("Requesting novel style learning from relay server for story: {}", request.getStory_id());

        try {
            Boolean response = relayServerWebClient.post()
                .uri("/ai/learn-novel-style")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Boolean.class)
                .timeout(Duration.ofSeconds(30))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("Relay server error during novel style learning: {} - {}",
                        e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.just(false);
                })
                .block();

            log.info("Novel style learning result: {}", response != null && response ? "success" : "failed");
            return response != null && response;

        } catch (Exception e) {
            log.error("Failed to learn novel style: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Request image generation from relay server
     */
    public ImageGenerationResponseDto generateImage(ImageGenerationRequestDto request) {
        log.info("Requesting image generation from relay server for node: {}", request.getNodeText());

        try {
            ImageGenerationResponseDto response = relayServerWebClient.post()
                .uri("/ai/generate-image")
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
