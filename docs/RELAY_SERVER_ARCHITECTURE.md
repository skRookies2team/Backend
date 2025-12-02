# Relay Server Architecture

## 개요

AI 서버들(분석 AI, 이미지 생성 AI 등)과의 통신을 전담하는 중계 서버 아키텍처 설계 문서입니다.

게임플레이 중 실시간 이미지 생성을 지원하기 위해 백엔드를 **비즈니스 로직 서버**와 **AI 중계 서버**로 분리합니다.

---

## 시스템 아키텍처

### 전체 구조도

```
┌─────────────┐
│  Frontend   │
│  (React)    │
└──────┬──────┘
       │ HTTP REST
       ▼
┌─────────────────────────────────────┐
│      Backend (Spring Boot)          │
│  - 비즈니스 로직                      │
│  - 게임 세션 관리                     │
│  - 사용자 인증                        │
│  - DB 관리 (MariaDB)                 │
└──────┬──────────────────────────────┘
       │ HTTP REST
       ▼
┌─────────────────────────────────────┐
│   Relay Server (Spring Boot)        │
│  - AI 서버 통신 전담                  │
│  - 요청/응답 포맷 변환                 │
│  - S3 업로드 처리                     │
│  - (선택) 이미지 캐싱                  │
└──────┬──────────────────────────────┘
       │
       ├─────► [분석 AI Server] (Python FastAPI)
       │       - 소설 분석
       │       - 캐릭터/게이지 추출
       │       - 스토리 생성
       │
       ├─────► [이미지 생성 AI Server] (Python)
       │       - 장면 이미지 생성
       │       - 캐릭터 이미지 생성
       │
       └─────► [기타 AI Server] (개발 예정)
                - TBD
```

### 책임 분리

| 서버 | 역할 | 기술 스택 |
|------|------|-----------|
| **Backend** | 비즈니스 로직, 게임플레이, DB, 인증 | Spring Boot 3.2, MariaDB, JWT |
| **Relay Server** | AI 통신, S3 업로드, 포맷 변환 | Spring Boot 3.2, WebClient, AWS SDK |
| **AI Servers** | AI 모델 실행 (분석, 이미지 생성 등) | Python, FastAPI |

---

## Relay Server API 명세

### Base URL
```
http://localhost:8080 (개발)
https://relay.yourdomain.com (프로덕션)
```

### 1. 소설 분석 API

**기존 기능 이관**: `StoryGenerationService.analyzeNovel()` → Relay Server

#### `POST /ai/analyze`

소설 텍스트를 분석하여 요약, 캐릭터, 게이지를 추출합니다.

**Request:**
```json
{
  "novelText": "어느 날 주인공은 낯선 세계에 떨어졌다..."
}
```

**Response:**
```json
{
  "summary": "주인공이 이세계로 떨어져 모험을 시작하는 이야기",
  "characters": [
    {
      "id": "char_001",
      "name": "주인공",
      "description": "평범한 고등학생"
    }
  ],
  "gauges": [
    {
      "id": "hope",
      "name": "희망",
      "description": "미래에 대한 희망과 긍정적인 마음"
    },
    {
      "id": "trust",
      "name": "신뢰",
      "description": "타인에 대한 신뢰와 관계"
    }
  ]
}
```

**처리 플로우:**
1. Backend → Relay Server로 요청
2. Relay Server → 분석 AI Server `/analyze` 호출
3. AI 응답 수신 및 포맷 변환
4. Backend로 응답 반환

---

### 2. 스토리 생성 API

**기존 기능 이관**: `StoryGenerationService.generateStory()` → Relay Server

#### `POST /ai/generate`

전체 스토리(에피소드, 노드, 선택지 등)를 생성하고 S3에 저장합니다.

**Request:**
```json
{
  "novelText": "소설 전체 텍스트...",
  "selectedGaugeIds": ["hope", "trust"],
  "numEpisodes": 3,
  "maxDepth": 3,
  "endingConfig": {
    "numFinalEndings": 3
  },
  "numEpisodeEndings": 2,
  "fileKey": "stories/uuid.json",
  "s3UploadUrl": "https://s3.presigned.url..."
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Story generated successfully",
  "data": {
    "metadata": {
      "totalEpisodes": 3,
      "totalNodes": 27
    },
    "context": { ... },
    "episodes": [ ... ]
  }
}
```

**처리 플로우:**
1. Backend → S3 Presigned URL 생성
2. Backend → Relay Server로 요청 (fileKey, s3UploadUrl 포함)
3. Relay Server → 분석 AI Server `/generate` 호출
4. AI가 스토리 생성 완료 후 S3 직접 업로드
5. Relay Server → Backend로 응답 반환

---

### 3. 이미지 생성 API ⭐ (신규)

**핵심 기능**: 게임플레이 중 각 노드의 장면 이미지를 실시간 생성합니다.

#### `POST /ai/generate-image`

노드 정보를 기반으로 이미지를 생성하고 S3에 업로드합니다.

**Request:**
```json
{
  "nodeText": "당신은 어두운 복도 끝에 서 있습니다. 멀리서 희미한 불빛이 보입니다.",
  "situation": "긴장감 넘치는 상황, 주인공이 선택의 기로에 섬",
  "npcEmotions": {
    "주인공": "긴장",
    "가이드": "불안"
  },
  "episodeTitle": "첫 만남",
  "episodeOrder": 1,
  "nodeDepth": 1,
  "imageStyle": "dark_fantasy",
  "additionalContext": "중세 판타지 세계관"
}
```

**Response:**
```json
{
  "imageUrl": "https://s3.ap-northeast-2.amazonaws.com/bucket/images/abc123.png",
  "fileKey": "images/abc123.png",
  "generatedAt": "2025-12-02T10:30:00Z"
}
```

**처리 플로우:**
1. Backend → Relay Server로 이미지 생성 요청
2. Relay Server → 이미지 생성 AI Server 호출
3. AI가 이미지 생성 (Stable Diffusion 등)
4. Relay Server → S3에 이미지 업로드
5. Relay Server → 이미지 URL 반환
6. Backend → GameStateResponseDto에 imageUrl 포함하여 Frontend로 응답

**동기 처리 (사용자 대기)**:
- 사용자가 선택을 하면 이미지 생성이 완료될 때까지 대기
- 로딩 스피너 표시 권장
- 예상 생성 시간: 5-15초

---

### 4. Health Check API

#### `GET /health`

Relay Server 및 연결된 AI 서버들의 상태를 확인합니다.

**Response:**
```json
{
  "status": "healthy",
  "relayServer": "up",
  "aiServers": {
    "analysisAi": {
      "status": "up",
      "url": "http://ai-analysis:8000"
    },
    "imageGenerationAi": {
      "status": "up",
      "url": "http://ai-image:8001"
    }
  }
}
```

---

## 게임플레이 이미지 생성 플로우

### 현재 플로우 (이미지 없음)

```
1. 사용자가 선택 (POST /api/game/{sessionId}/choice)
   ↓
2. GameService.makeChoice() 실행
   ↓
3. 다음 노드 찾기 (findNextNode)
   ↓
4. GameStateResponseDto 생성
   - nodeText
   - nodeDetails
   - choices
   ↓
5. Frontend로 응답 반환
```

### 새로운 플로우 (이미지 포함) ⭐

```
1. 사용자가 선택 (POST /api/game/{sessionId}/choice)
   ↓
2. GameService.makeChoice() 실행
   ↓
3. 다음 노드 찾기 (findNextNode)
   ↓
4. 이미지 생성 요청 (NEW!)
   Backend → Relay Server POST /ai/generate-image
   {
     nodeText: nextNode.getText(),
     situation: nextNode.getDetails().getSituation(),
     npcEmotions: nextNode.getDetails().getNpcEmotions(),
     ...
   }
   ↓
5. Relay Server → 이미지 생성 AI 호출
   ↓
6. 이미지 생성 완료 → S3 업로드
   ↓
7. Relay Server → imageUrl 반환
   ↓
8. GameStateResponseDto 생성 (이미지 URL 포함)
   - nodeText
   - nodeDetails
   - choices
   - imageUrl ← NEW!
   ↓
9. Frontend로 응답 반환
   ↓
10. Frontend에서 텍스트 + 이미지 동시 표시
```

---

## Backend 코드 변경 사항

### 1. GameStateResponseDto 수정

**파일**: `src/main/java/com/story/game/gameplay/dto/GameStateResponseDto.java`

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameStateResponseDto {
    private String sessionId;
    private String currentEpisodeId;
    private String currentNodeId;
    private Map<String, Integer> gaugeStates;
    private Map<String, Integer> accumulatedTags;

    // Current content
    private String episodeTitle;
    private String introText;
    private String nodeText;
    private StoryNodeDto.StoryNodeDetailDto nodeDetails;
    private List<StoryChoiceDto> choices;

    // NEW: 이미지 URL 추가
    private String imageUrl;

    // Gauge info for display
    private List<GaugeDto> gaugeDefinitions;

    // Progress info
    private Boolean isEpisodeEnd;
    private Boolean isGameEnd;
    private EpisodeEndingDto episodeEnding;
    private FinalEndingDto finalEnding;
}
```

### 2. 이미지 생성 요청 DTO 추가

**새 파일**: `src/main/java/com/story/game/ai/dto/ImageGenerationRequestDto.java`

```java
package com.story.game.ai.dto;

import lombok.*;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationRequestDto {
    private String nodeText;
    private String situation;
    private Map<String, String> npcEmotions;
    private String episodeTitle;
    private Integer episodeOrder;
    private Integer nodeDepth;
    private String imageStyle;
    private String additionalContext;
}
```

**새 파일**: `src/main/java/com/story/game/ai/dto/ImageGenerationResponseDto.java`

```java
package com.story.game.ai.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageGenerationResponseDto {
    private String imageUrl;
    private String fileKey;
    private String generatedAt;
}
```

### 3. RelayServerClient 서비스 추가

**새 파일**: `src/main/java/com/story/game/ai/service/RelayServerClient.java`

```java
package com.story.game.ai.service;

import com.story.game.ai.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class RelayServerClient {

    private final WebClient relayServerWebClient;

    public ImageGenerationResponseDto generateImage(ImageGenerationRequestDto request) {
        log.info("Requesting image generation for node: {}", request.getNodeText());

        ImageGenerationResponseDto response = relayServerWebClient.post()
            .uri("/ai/generate-image")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ImageGenerationResponseDto.class)
            .timeout(Duration.ofSeconds(30)) // 이미지 생성 타임아웃 30초
            .block();

        if (response == null) {
            throw new RuntimeException("No response from relay server");
        }

        log.info("Image generated successfully: {}", response.getImageUrl());
        return response;
    }
}
```

### 4. WebClientConfig 수정

**파일**: `src/main/java/com/story/game/infrastructure/config/WebClientConfig.java`

```java
@Configuration
public class WebClientConfig {

    @Value("${relay-server.url:http://localhost:8080}")
    private String relayServerUrl;

    @Value("${relay-server.timeout:30000}") // 30초
    private int timeout;

    @Bean
    public WebClient relayServerWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                .responseTimeout(Duration.ofMillis(timeout))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(timeout, TimeUnit.MILLISECONDS)));

        return WebClient.builder()
                .baseUrl(relayServerUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
```

### 5. GameService 수정

**파일**: `src/main/java/com/story/game/gameplay/service/GameService.java`

**기존 코드 (line 95-138):**
```java
@Transactional
public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex) {
    // ... 기존 로직 ...

    StoryNodeDto nextNode = findNextNode(currentEpisode, currentNode.getId(), choiceIndex);

    if (nextNode != null) {
        session.setCurrentNodeId(nextNode.getId());
        session.getVisitedNodes().add(nextNode.getId());
        session = gameSessionRepository.save(session);

        return buildGameStateResponse(session, fullStory, currentEpisode, nextNode, false);
    }
    // ...
}
```

**수정 후:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class GameService {

    private final RelayServerClient relayServerClient; // 추가

    @Transactional
    public GameStateResponseDto makeChoice(String sessionId, Integer choiceIndex) {
        // ... 기존 로직 ...

        StoryNodeDto nextNode = findNextNode(currentEpisode, currentNode.getId(), choiceIndex);

        if (nextNode != null) {
            session.setCurrentNodeId(nextNode.getId());
            session.getVisitedNodes().add(nextNode.getId());
            session = gameSessionRepository.save(session);

            // 이미지 생성 요청 (NEW!)
            String imageUrl = generateNodeImage(nextNode, currentEpisode);

            return buildGameStateResponse(session, fullStory, currentEpisode, nextNode, false, imageUrl);
        }
        // ...
    }

    // 새 메서드 추가
    private String generateNodeImage(StoryNodeDto node, EpisodeDto episode) {
        try {
            ImageGenerationRequestDto request = ImageGenerationRequestDto.builder()
                .nodeText(node.getText())
                .situation(node.getDetails() != null ? node.getDetails().getSituation() : null)
                .npcEmotions(node.getDetails() != null ? node.getDetails().getNpcEmotions() : null)
                .episodeTitle(episode.getTitle())
                .episodeOrder(episode.getOrder())
                .nodeDepth(node.getDepth())
                .build();

            ImageGenerationResponseDto response = relayServerClient.generateImage(request);
            return response.getImageUrl();
        } catch (Exception e) {
            log.error("Failed to generate image for node {}: {}", node.getId(), e.getMessage());
            return null; // 이미지 생성 실패 시 null 반환 (게임은 계속 진행)
        }
    }

    // buildGameStateResponse 메서드 수정 (imageUrl 파라미터 추가)
    private GameStateResponseDto buildGameStateResponse(
            GameSession session,
            FullStoryDto fullStory,
            EpisodeDto episode,
            StoryNodeDto node,
            boolean showIntro,
            String imageUrl) { // 추가
        return GameStateResponseDto.builder()
            .sessionId(session.getId())
            .currentEpisodeId(session.getCurrentEpisodeId())
            .currentNodeId(session.getCurrentNodeId())
            .gaugeStates(session.getGaugeStates())
            .accumulatedTags(session.getAccumulatedTags())
            .episodeTitle(episode.getTitle())
            .introText(showIntro ? episode.getIntroText() : null)
            .nodeText(node.getText())
            .nodeDetails(node.getDetails())
            .choices(node.getChoices())
            .imageUrl(imageUrl) // 추가
            .gaugeDefinitions(fullStory.getContext().getSelectedGauges())
            .isEpisodeEnd(false)
            .isGameEnd(false)
            .build();
    }
}
```

### 6. StoryGenerationService 수정

**기존**: AI 서버 직접 호출
**변경 후**: Relay Server 호출

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class StoryGenerationService {

    private final WebClient relayServerWebClient; // aiServerWebClient → relayServerWebClient로 변경

    public NovelAnalysisResponseDto analyzeNovel(String novelText) {
        // /analyze 엔드포인트는 동일
        // 단, relayServerWebClient 사용
    }

    public StoryData generateStory(GenerateStoryRequestDto request) {
        // /generate 엔드포인트는 동일
        // 단, relayServerWebClient 사용
    }
}
```

---

## Relay Server 구현

### 프로젝트 구조

```
relay-server/
├── src/main/java/com/story/relay/
│   ├── RelayServerApplication.java
│   ├── controller/
│   │   └── AiController.java
│   ├── service/
│   │   ├── AnalysisAiClient.java
│   │   ├── ImageGenerationAiClient.java
│   │   └── S3UploadService.java
│   ├── dto/
│   │   ├── ImageGenerationRequestDto.java
│   │   └── ImageGenerationResponseDto.java
│   └── config/
│       └── WebClientConfig.java
├── src/main/resources/
│   └── application.yml
└── build.gradle
```

### application.yml

```yaml
server:
  port: 8081

spring:
  application:
    name: relay-server

# AI 서버 URL 설정
ai-servers:
  analysis:
    url: http://localhost:8000
    timeout: 600000  # 10분
  image-generation:
    url: http://localhost:8001
    timeout: 30000   # 30초

# AWS S3 설정
aws:
  s3:
    bucket: ${AWS_S3_BUCKET}
    region: ${AWS_S3_REGION:ap-northeast-2}
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}

logging:
  level:
    com.story.relay: DEBUG
```

### AiController.java

```java
package com.story.relay.controller;

import com.story.relay.dto.*;
import com.story.relay.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AnalysisAiClient analysisAiClient;
    private final ImageGenerationAiClient imageGenerationAiClient;

    @PostMapping("/analyze")
    public ResponseEntity<NovelAnalysisResponseDto> analyzeNovel(
            @RequestBody NovelAnalysisRequestDto request) {
        log.info("Analyze request received, text length: {}", request.getNovelText().length());
        NovelAnalysisResponseDto response = analysisAiClient.analyze(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate")
    public ResponseEntity<StoryGenerationResponseDto> generateStory(
            @RequestBody StoryGenerationRequestDto request) {
        log.info("Generate story request received");
        StoryGenerationResponseDto response = analysisAiClient.generate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/generate-image")
    public ResponseEntity<ImageGenerationResponseDto> generateImage(
            @RequestBody ImageGenerationRequestDto request) {
        log.info("Generate image request received for: {}", request.getNodeText());
        ImageGenerationResponseDto response = imageGenerationAiClient.generateImage(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "healthy");
        health.put("relayServer", "up");

        Map<String, Object> aiServers = new HashMap<>();
        aiServers.put("analysisAi", checkAiHealth(analysisAiClient));
        aiServers.put("imageGenerationAi", checkAiHealth(imageGenerationAiClient));
        health.put("aiServers", aiServers);

        return ResponseEntity.ok(health);
    }
}
```

### ImageGenerationAiClient.java

```java
package com.story.relay.service;

import com.story.relay.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationAiClient {

    private final WebClient imageGenerationWebClient;
    private final S3UploadService s3UploadService;

    public ImageGenerationResponseDto generateImage(ImageGenerationRequestDto request) {
        log.info("Calling image generation AI for node: {}", request.getNodeText());

        // 1. AI 서버에 이미지 생성 요청
        byte[] imageBytes = imageGenerationWebClient.post()
            .uri("/generate-image")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(byte[].class)
            .timeout(Duration.ofSeconds(30))
            .block();

        if (imageBytes == null || imageBytes.length == 0) {
            throw new RuntimeException("No image data received from AI server");
        }

        log.info("Image generated by AI, size: {} bytes", imageBytes.length);

        // 2. S3에 업로드
        String fileKey = "images/" + UUID.randomUUID().toString() + ".png";
        String imageUrl = s3UploadService.uploadImage(fileKey, imageBytes);

        log.info("Image uploaded to S3: {}", imageUrl);

        return ImageGenerationResponseDto.builder()
            .imageUrl(imageUrl)
            .fileKey(fileKey)
            .generatedAt(java.time.Instant.now().toString())
            .build();
    }
}
```

---

## 환경 변수 설정

### Backend (기존 프로젝트)

**application.yml 추가:**
```yaml
relay-server:
  url: ${RELAY_SERVER_URL:http://localhost:8081}
  timeout: 30000  # 30초
```

**.env 추가:**
```bash
# Relay Server
RELAY_SERVER_URL=http://localhost:8081
```

### Relay Server (새 프로젝트)

**.env:**
```bash
# AI Servers
AI_ANALYSIS_URL=http://localhost:8000
AI_IMAGE_GENERATION_URL=http://localhost:8001

# AWS S3
AWS_S3_BUCKET=your-bucket-name
AWS_S3_REGION=ap-northeast-2
AWS_ACCESS_KEY=your-access-key
AWS_SECRET_KEY=your-secret-key
```

---

## 구현 로드맵

### Phase 1: Relay Server 기본 구조 생성
- [ ] Spring Boot 프로젝트 생성
- [ ] WebClient 설정 (AI 서버들)
- [ ] Health Check API 구현
- [ ] 기본 컨트롤러/서비스 구조 생성

### Phase 2: 기존 기능 이관
- [ ] `/ai/analyze` 엔드포인트 구현 (분석 AI 연동)
- [ ] `/ai/generate` 엔드포인트 구현 (스토리 생성 AI 연동)
- [ ] Backend에서 Relay Server 호출하도록 변경
- [ ] 테스트 및 검증

### Phase 3: 이미지 생성 기능 추가
- [ ] `/ai/generate-image` 엔드포인트 구현
- [ ] S3 업로드 서비스 구현
- [ ] Backend DTO 수정 (imageUrl 필드 추가)
- [ ] GameService 수정 (이미지 생성 통합)
- [ ] 이미지 생성 AI 서버 개발 대기 → Mock 응답으로 테스트

### Phase 4: 최적화 및 배포
- [ ] 에러 핸들링 강화
- [ ] 로깅 및 모니터링
- [ ] 캐싱 전략 (선택)
- [ ] 성능 테스트
- [ ] 프로덕션 배포

---

## 테스트 계획

### 1. Relay Server 단독 테스트
```bash
# Health check
curl http://localhost:8081/health

# 분석 API 테스트
curl -X POST http://localhost:8081/ai/analyze \
  -H "Content-Type: application/json" \
  -d '{"novelText": "테스트 소설..."}'

# 이미지 생성 API 테스트 (Mock)
curl -X POST http://localhost:8081/ai/generate-image \
  -H "Content-Type: application/json" \
  -d '{
    "nodeText": "어두운 복도",
    "situation": "긴장감"
  }'
```

### 2. Backend 통합 테스트
```bash
# 게임 시작
POST /api/game/start

# 선택 (이미지 생성 포함)
POST /api/game/{sessionId}/choice
{
  "choiceIndex": 0
}

# 응답에 imageUrl 포함 확인
```

### 3. E2E 테스트
1. Frontend에서 게임 시작
2. 선택지 클릭
3. 로딩 표시 (이미지 생성 중)
4. 텍스트 + 이미지 동시 표시 확인

---

## 주의사항 및 권장사항

### 성능 고려사항
- **이미지 생성 시간**: 5-15초 예상 → 사용자 대기 시간
- **타임아웃 설정**: Relay Server (30초), Backend (30초)
- **S3 업로드**: Presigned URL 사용 권장

### 에러 핸들링
- 이미지 생성 실패 시 게임은 계속 진행 (imageUrl = null)
- Frontend에서 fallback 이미지 또는 텍스트만 표시
- 재시도 로직 고려 (1-2회)

### 보안
- AI 서버들은 내부 네트워크에서만 접근 가능하도록 설정
- Relay Server는 Backend에서만 호출 가능하도록 제한 (API Key 또는 IP Whitelist)

### 확장성
- 향후 AI 서버 추가 시 Relay Server에만 클라이언트 추가
- Backend는 변경 불필요
- 이미지 캐싱 전략 (동일 노드 재방문 시)

---

## FAQ

### Q1. 이미지 생성 AI가 아직 없는데 어떻게 테스트하나요?
**A**: Mock 응답을 반환하는 임시 서버를 만들거나, Relay Server에서 더미 이미지 URL을 반환하도록 구현합니다.

```java
// 임시 구현
public ImageGenerationResponseDto generateImage(ImageGenerationRequestDto request) {
    // TODO: 실제 AI 서버 연동 대기
    return ImageGenerationResponseDto.builder()
        .imageUrl("https://via.placeholder.com/800x600")
        .fileKey("mock/image.png")
        .generatedAt(Instant.now().toString())
        .build();
}
```

### Q2. 모든 노드에 이미지를 생성해야 하나요?
**A**: 선택적으로 구현 가능합니다. 예를 들어:
- 중요한 노드에만 이미지 생성 (depth 0, 1만)
- 에피소드 시작 노드만 이미지 생성
- 설정에 따라 ON/OFF

### Q3. 이미지 캐싱은 어떻게 하나요?
**A**: 동일한 nodeId에 대해 이미지를 재생성하지 않도록 캐싱:
- DB에 `node_images` 테이블 추가 (nodeId → imageUrl)
- Redis 캐싱
- S3 fileKey 규칙 사용 (예: `images/{storyId}/{nodeId}.png`)

---

## 참고 문서

- [ARCHITECTURE.md](./ARCHITECTURE.md) - 전체 시스템 아키텍처
- [CODING_CONVENTIONS.md](./CODING_CONVENTIONS.md) - 코딩 규칙
- [IMAGE_GENERATION_FLOW.md](./IMAGE_GENERATION_FLOW.md) - 이미지 생성 플로우 상세
- [AI Server Repository](https://github.com/skRookies2team/AI/tree/feature/kwak) - Python AI 서버

---

**Last Updated**: 2025-12-02
**Author**: System Architecture Team
