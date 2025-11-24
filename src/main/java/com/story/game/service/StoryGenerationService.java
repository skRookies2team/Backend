package com.story.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.dto.*;
import com.story.game.entity.StoryData;
import com.story.game.repository.StoryDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryGenerationService {

    private final WebClient aiServerWebClient;
    private final StoryDataRepository storyDataRepository;
    private final ObjectMapper objectMapper;

    /**
     * Analyze novel text to extract summary, characters, and gauges
     */
    public NovelAnalysisResponseDto analyzeNovel(String novelText) {
        log.info("=== Analyze Novel Request ===");
        log.info("Novel text length: {} characters", novelText.length());

        NovelAnalysisRequestDto request = NovelAnalysisRequestDto.builder()
                .novelText(novelText)
                .build();

        NovelAnalysisResponseDto response = aiServerWebClient.post()
                .uri("/analyze")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(NovelAnalysisResponseDto.class)
                .timeout(Duration.ofMinutes(3))
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("AI server error during analysis: {} - {}",
                            e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("AI analysis failed: " + e.getMessage()));
                })
                .block();

        if (response == null) {
            throw new RuntimeException("No response from AI server during analysis");
        }

        log.info("Novel analysis completed: {} characters, {} gauges extracted",
                response.getCharacters().size(), response.getGauges().size());

        return response;
    }

    @Transactional
    public StoryData generateStory(GenerateStoryRequestDto request) {
        log.info("=== Generate Story Request ===");
        log.info("Title: {}, Episodes: {}, Depth: {}, Gauges: {}",
                request.getTitle(), request.getNumEpisodes(),
                request.getMaxDepth(), request.getSelectedGaugeIds());

        // Build request for Python AI server
        StoryGenerationRequestDto aiRequest = StoryGenerationRequestDto.builder()
                .novelText(request.getNovelText())
                .selectedGaugeIds(request.getSelectedGaugeIds())
                .numEpisodes(request.getNumEpisodes())
                .maxDepth(request.getMaxDepth())
                .endingConfig(request.getEndingConfig())
                .numEpisodeEndings(request.getNumEpisodeEndings())
                .build();

        // Call AI server
        StoryGenerationResponseDto response = aiServerWebClient.post()
                .uri("/generate")
                .bodyValue(aiRequest)
                .retrieve()
                .bodyToMono(StoryGenerationResponseDto.class)
                .timeout(Duration.ofMinutes(10))  // Story generation can take time
                .onErrorResume(WebClientResponseException.class, e -> {
                    log.error("AI server error during generation: {} - {}",
                            e.getStatusCode(), e.getResponseBodyAsString());
                    return Mono.error(new RuntimeException("AI server error: " + e.getMessage()));
                })
                .block();

        if (response == null || !"success".equals(response.getStatus())) {
            String errorMsg = response != null ? response.getMessage() : "No response from AI server";
            throw new RuntimeException("Story generation failed: " + errorMsg);
        }

        // Convert story data to JSON string for storage
        String storyJson;
        try {
            storyJson = objectMapper.writeValueAsString(response.getData());
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize story data", e);
        }

        // Save to database
        StoryData storyData = StoryData.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .storyJson(storyJson)
                .totalEpisodes(response.getData().getMetadata().getTotalEpisodes())
                .totalNodes(response.getData().getMetadata().getTotalNodes())
                .build();

        storyData = storyDataRepository.save(storyData);
        log.info("Story generated and saved: id={}, title={}", storyData.getId(), storyData.getTitle());

        return storyData;
    }

    public boolean checkAiServerHealth() {
        try {
            String response = aiServerWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            return response != null;
        } catch (Exception e) {
            log.warn("AI server health check failed: {}", e.getMessage());
            return false;
        }
    }
}
