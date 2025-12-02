package com.story.game.creation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.story.game.common.dto.FullStoryDto;
import com.story.game.common.entity.StoryData;
import com.story.game.common.repository.StoryDataRepository;
import com.story.game.creation.dto.GenerateStoryRequestDto;
import com.story.game.creation.dto.NovelAnalysisRequestDto;
import com.story.game.creation.dto.NovelAnalysisResponseDto;
import com.story.game.creation.dto.StoryGenerationRequestDto;
import com.story.game.creation.dto.StoryGenerationResponseDto;
import com.story.game.infrastructure.s3.S3Service;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryGenerationService {

    private final WebClient relayServerWebClient;
    private final StoryDataRepository storyDataRepository;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;

    /**
     * Analyze novel text to extract summary, characters, and gauges
     */
    public NovelAnalysisResponseDto analyzeNovel(String novelText) {
        log.info("=== Analyze Novel Request ===");
        log.info("Novel text length: {} characters", novelText.length());

        NovelAnalysisRequestDto request = NovelAnalysisRequestDto.builder()
            .novelText(novelText)
            .build();

        NovelAnalysisResponseDto response = relayServerWebClient.post()
            .uri("/ai/analyze")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(NovelAnalysisResponseDto.class)
            .timeout(Duration.ofMinutes(3))
            .onErrorResume(WebClientResponseException.class, e -> {
                log.error("Relay server error during analysis: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
                return Mono.error(new RuntimeException("AI analysis failed: " + e.getMessage()));
            })
            .block();

        if (response == null) {
            throw new RuntimeException("No response from relay server during analysis");
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

        // 1. Generate a unique file key and a pre-signed URL
        String fileKey = "stories/" + java.util.UUID.randomUUID().toString() + ".json";
        S3Service.PresignedUrlInfo presignedUrlInfo = s3Service.generatePresignedUploadUrl(
            fileKey);

        // 2. Build request for Python AI server
        StoryGenerationRequestDto aiRequest = StoryGenerationRequestDto.builder()
            .novelText(request.getNovelText())
            .selectedGaugeIds(request.getSelectedGaugeIds())
            .numEpisodes(request.getNumEpisodes())
            .maxDepth(request.getMaxDepth())
            .endingConfig(request.getEndingConfig())
            .numEpisodeEndings(request.getNumEpisodeEndings())
            .fileKey(presignedUrlInfo.getFileKey())
            .s3UploadUrl(presignedUrlInfo.getUrl())
            .build();

        // 3. Call relay server
        StoryGenerationResponseDto response = relayServerWebClient.post()
            .uri("/ai/generate")
            .bodyValue(aiRequest)
            .retrieve()
            .bodyToMono(StoryGenerationResponseDto.class)
            .timeout(Duration.ofMinutes(10))  // Story generation can take time
            .onErrorResume(WebClientResponseException.class, e -> {
                log.error("Relay server error during generation: {} - {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
                return Mono.error(new RuntimeException("Relay server error: " + e.getMessage()));
            })
            .block();

        if (response == null || !"success".equals(response.getStatus()) || response.getData() == null || response.getData().getMetadata() == null) {
            String errorMsg = response != null ? response.getMessage() : "No response from relay server";
            throw new RuntimeException("Story generation failed: " + errorMsg);
        }

        // 4. Save to database with fileKey
        StoryData storyData = StoryData.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .storyFileKey(fileKey)
            .totalEpisodes(response.getData().getMetadata().getTotalEpisodes())
            .totalNodes(response.getData().getMetadata().getTotalNodes())
            .build();

        storyData = storyDataRepository.save(storyData);
        log.info("Story generated and saved with fileKey: id={}, title={}, fileKey={}", storyData.getId(),
            storyData.getTitle(), storyData.getStoryFileKey());

        return storyData;
    }

    public boolean checkAiServerHealth() {
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
