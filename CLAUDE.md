# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the project
./gradlew build

# Clean build artifacts
./gradlew clean

# Run the application
./gradlew bootRun

# Run tests
./gradlew test

# Run a specific test class
./gradlew test --tests ClassName
```

## Project Overview

Interactive story game player backend built with Spring Boot 3.2 + JPA + MariaDB.

**Main entry point**: `src/main/java/com/story/game/StoryGameApplication.java`

## Architecture

```
com.story.game/
├── config/         # WebConfig (CORS)
├── controller/     # REST API endpoints
│   ├── StoryManagementController  # 스토리 생성 관리
│   └── GameController            # 게임 플레이
├── dto/            # Data transfer objects
├── entity/         # JPA entities
│   ├── StoryCreation             # 스토리 생성 진행 상태
│   ├── StoryData                 # 완성된 스토리 데이터
│   └── GameSession               # 게임 세션
├── repository/     # Data access layer
└── service/        # Business logic
    ├── StoryManagementService    # 스토리 생성 관리
    ├── StoryGenerationService    # AI 서버 연동 (레거시)
    └── GameService               # 게임 로직
```

### Core Flow

#### 1. Story Generation Flow (새로운 세분화된 프로세스)
1. 소설 업로드 → AI 분석 시작 (`StoryCreation` 생성, status: ANALYZING)
2. 요약/캐릭터/게이지 추출 완료 (status: GAUGES_READY)
3. 사용자가 게이지 2개 선택 (status: GAUGES_SELECTED)
4. 생성 설정 입력 (에피소드 수, depth, 엔딩 타입) (status: CONFIGURED)
5. 스토리 생성 시작 (status: GENERATING)
6. AI 서버에서 스토리 생성 (progress 폴링)
7. 생성 완료 → `StoryData` 저장 (status: COMPLETED)

#### 2. Game Play Flow
1. 플레이어가 스토리 선택 → GameSession 생성
2. 선택하기 → 태그 누적, 다음 노드로 이동
3. Leaf node 도달 → 에피소드 엔딩 평가, 게이지 변경
4. 모든 에피소드 완료 → 최종 엔딩 평가

### Key Components

- **StoryCreation**: 스토리 생성 과정 추적 (분석 결과, 진행 상태, 설정 등)
- **StoryData**: 완성된 스토리 JSON 저장
- **GameSession**: 게임 세션 상태 (현재 노드, 게이지, 태그, 방문 노드)
- **StoryManagementService**: 스토리 생성의 각 단계 관리
- **GameService**: 게임 로직 (노드 탐색, 조건 평가, 엔딩 결정)

## REST API

### Story Generation API (StoryManagementController)
세분화된 스토리 생성 프로세스 - 자세한 문서: `STORY_GENERATION_API.md`

```
POST /api/stories/upload                  - 소설 직접 업로드 및 분석 시작
POST /api/stories/upload-from-s3          - S3에서 소설 읽어서 분석 시작 🆕
GET  /api/stories/{id}/summary            - 요약 조회
GET  /api/stories/{id}/characters         - 캐릭터 조회
GET  /api/stories/{id}/gauges             - 게이지 5개 제안 조회
POST /api/stories/{id}/gauges/select      - 게이지 2개 선택
POST /api/stories/{id}/config             - 생성 설정 (에피소드 수, depth, 엔딩 타입)
POST /api/stories/{id}/generate           - 스토리 생성 시작
GET  /api/stories/{id}/progress           - 생성 진행률 조회 (폴링용)
GET  /api/stories/{id}/result             - 생성 완료 결과 조회 (preview)
GET  /api/stories/{id}/data               - 전체 스토리 데이터 조회 (게임 구성용)
```

### File Upload API (UploadController) 🆕
S3를 이용한 파일 업로드 API

```
GET  /api/upload/presigned-url            - Pre-signed URL 생성 (업로드용)
GET  /api/upload/download-url             - Pre-signed URL 생성 (다운로드용)
```

### Game Play API (GameController)
게임 플레이 관련 API

```
POST /api/game/start                      - 게임 시작 (body: {storyDataId})
GET  /api/game/{sessionId}                - 현재 상태 조회
POST /api/game/{sessionId}/choice         - 선택하기 (body: {choiceIndex})
GET  /api/game/stories                    - 스토리 목록 조회
GET  /api/game/stories/{id}/data          - 전체 스토리 데이터 조회 (storyDataId로) 🆕
POST /api/game/stories                    - 스토리 JSON 업로드 (레거시)
POST /api/game/stories/analyze            - 소설 분석 (레거시)
POST /api/game/stories/generate           - 스토리 생성 (레거시)
GET  /api/game/ai/health                  - AI 서버 상태 확인
```

## Configuration

Database connection in `src/main/resources/application.yml`:
- Default DB: `story_game` on localhost:3306
- Set `DB_PASSWORD` env variable or update password in yml
- Set `AI_SERVER_URL` env variable (default: http://localhost:8000)

AWS S3 configuration (for file upload):
- Set `AWS_S3_BUCKET` env variable (your S3 bucket name)
- Set `AWS_S3_REGION` env variable (default: ap-northeast-2)
- Set `AWS_ACCESS_KEY` env variable (AWS access key)
- Set `AWS_SECRET_KEY` env variable (AWS secret key)

## Python AI Integration

This backend plays stories generated by the Python AI engine at:
https://github.com/skRookies2team/AI/tree/feature/kwak

The JSON structure from Python maps to DTOs in `com.story.game.dto`.
