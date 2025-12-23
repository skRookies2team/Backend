# Story Game Backend

인터랙티브 스토리 게임 플레이어 백엔드 서버입니다. Python AI 엔진이 생성한 분기형 스토리를 플레이할 수 있습니다.

## 기술 스택

- Java 17
- Spring Boot 3.2
- JPA / Hibernate
- MariaDB
- WebClient (AI 서버 연동)

## 사전 요구사항

- Java 17 이상
- MariaDB 10.x 이상
- Python AI 서버 ([skRookies2team/AI](https://github.com/skRookies2team/AI/tree/feature/kwak))

## 설치 및 실행

### 1. 데이터베이스 설정

MariaDB에 데이터베이스를 생성합니다.

```sql
CREATE DATABASE story_game CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 환경 변수 설정

`.env.example` 파일을 복사하여 `.env` 파일을 생성하고 실제 값으로 수정합니다.

```bash
# .env 파일 생성
cp .env.example .env

# .env 파일 편집
DB_PASSWORD=your_db_password
AI_SERVER_URL=http://localhost:8000
```

> 📖 자세한 설정 방법은 [ENV_SETUP.md](docs/ENV_SETUP.md) 참고

또는 환경 변수를 직접 설정:

```bash
# Windows
set DB_PASSWORD=your_db_password

# Linux/Mac
export DB_PASSWORD=your_db_password
```

### 3. Python AI 서버 실행

```bash
cd AI
pip install -r requirements.txt
uvicorn api:app --reload --port 8000
```

AI 서버가 정상 실행되면 http://localhost:8000/health 에서 상태를 확인할 수 있습니다.

### 4. Spring Boot 서버 실행

```bash
# Windows
gradlew.bat bootRun

# Linux/Mac
./gradlew bootRun
```

서버가 http://localhost:8080 에서 실행됩니다.

## API 사용법

### 스토리 생성 (AI 서버 연동)

소설 텍스트를 입력하면 AI가 인터랙티브 스토리를 생성합니다.

```bash
curl -X POST http://localhost:8080/api/game/stories/generate \
  -H "Content-Type: application/json" \
  -d '{
    "title": "파리대왕",
    "description": "무인도에 불시착한 소년들의 생존 이야기",
    "novelText": "소설 전체 텍스트를 여기에 입력...",
    "numEpisodes": 3,
    "maxDepth": 2
  }'
```

**파라미터:**
- `title`: 스토리 제목 (필수)
- `description`: 스토리 설명
- `novelText`: 원본 소설 텍스트 (필수)
- `numEpisodes`: 에피소드 수 (1-10, 기본값 3)
- `maxDepth`: 스토리 트리 깊이 (1-5, 기본값 2)

> ⚠️ 스토리 생성은 AI가 처리하므로 최대 5분까지 소요될 수 있습니다.

### 게임 시작

생성된 스토리로 새 게임을 시작합니다.

```bash
curl -X POST http://localhost:8080/api/game/start \
  -H "Content-Type: application/json" \
  -d '{"storyDataId": 1}'
```

**응답 예시:**
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "currentEpisodeId": "ep1",
  "currentNodeId": "node_0",
  "episodeTitle": "첫 만남",
  "introText": "해가 뜨자 소년들이 모여들었다...",
  "nodeText": "랠프는 소라를 들어 올렸다...",
  "choices": [
    {"text": "소라를 불어 모두를 모은다", "tags": ["leadership", "cooperative"]},
    {"text": "혼자 섬을 탐험한다", "tags": ["independent", "curious"]},
    {"text": "잭과 대립한다", "tags": ["aggressive", "confrontational"]}
  ],
  "gaugeStates": {"hope": 50, "trust": 50, "civilization": 50}
}
```

### 선택지 선택

선택지를 선택하여 스토리를 진행합니다.

```bash
curl -X POST http://localhost:8080/api/game/{sessionId}/choice \
  -H "Content-Type: application/json" \
  -d '{"choiceIndex": 0}'
```

### 현재 상태 조회

게임 세션의 현재 상태를 조회합니다.

```bash
curl -X GET http://localhost:8080/api/game/{sessionId}
```

### 스토리 목록 조회

저장된 모든 스토리를 조회합니다.

```bash
curl -X GET http://localhost:8080/api/game/stories
```

### AI 서버 상태 확인

Python AI 서버의 연결 상태를 확인합니다.

```bash
curl -X GET http://localhost:8080/api/game/ai/health
```

## API 문서 (Swagger)

서버 실행 후 다음 URL에서 Swagger UI를 통해 모든 API를 확인하고 테스트할 수 있습니다.

```
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON 스펙:
```
http://localhost:8080/v3/api-docs
```

## 📚 Documentation

프로젝트 문서는 `docs` 폴더에서 확인할 수 있습니다:

### API 가이드
- **[스토리 생성 API](docs/STORY_GENERATION_API.md)** - 9단계 세분화 프로세스
- **[프론트엔드 연동 가이드](docs/FRONTEND_INTEGRATION_GUIDE.md)** - S3 파일 업로드 및 통합

### 시스템 통합
- **[AI 서버 S3 통합](docs/AI_SERVER_S3_INTEGRATION.md)** - AI 서버와 S3 직접 연동 가이드

### 설정 및 배포
- **[환경 설정](docs/ENV_SETUP.md)** - 데이터베이스, AWS, 환경 변수 설정
- **[Gemini 설정](docs/GEMINI.md)** - Gemini AI 통합 설정

## 연결 테스트

### 자동 테스트 스크립트

모든 연결 상태를 한 번에 확인:

```bash
# Windows
test-connection.bat

# Linux/Mac
chmod +x test-connection.sh
./test-connection.sh
```

### 수동 테스트

**1. 전체 시스템 상태 확인**
```bash
curl http://localhost:8080/api/health
```

**2. 데이터베이스 연결 확인**
```bash
curl http://localhost:8080/api/health/database
```

**3. AI 서버 연결 확인**
```bash
curl http://localhost:8080/api/health/ai-server
```

**4. 샘플 데이터로 게임 테스트**

AI 서버 없이 샘플 스토리로 테스트:
```bash
# 샘플 스토리 업로드
curl -X POST "http://localhost:8080/api/game/stories?title=테스트스토리&description=샘플데이터" \
  -H "Content-Type: application/json" \
  -d @test-data/sample-story.json

# 게임 시작
curl -X POST http://localhost:8080/api/game/start \
  -H "Content-Type: application/json" \
  -d '{"storyDataId": 1}'
```

## 게임 플로우

```
1. 스토리 생성 (/stories/generate)
   └─ Python AI가 소설을 분석하여 분기형 스토리 생성

2. 게임 시작 (/start)
   └─ 새 세션 생성, 초기 게이지 설정 (50)

3. 선택지 선택 (/choice) - 반복
   ├─ 선택한 태그 누적
   ├─ 다음 노드로 이동
   └─ 리프 노드 도달 시 에피소드 엔딩 평가

4. 에피소드 완료
   ├─ 엔딩 조건에 따라 게이지 변화 적용
   └─ 다음 에피소드로 이동

5. 모든 에피소드 완료
   └─ 최종 엔딩 평가 (게이지 기반)
```

## 설정 파일

`src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mariadb://localhost:3306/story_game
    username: root
    password: ${DB_PASSWORD:password}

server:
  port: 8080

ai-server:
  url: ${AI_SERVER_URL:http://localhost:8000}
```

## 프로젝트 구조

```
src/main/java/com/story/game/
├── StoryGameApplication.java    # 메인 진입점
├── config/                      # 설정 (CORS, WebClient)
├── controller/                  # REST API 컨트롤러
├── dto/                         # 데이터 전송 객체
├── entity/                      # JPA 엔티티
├── repository/                  # 데이터 접근 계층
└── service/                     # 비즈니스 로직
```

## 라이선스

MIT License
# test

# Auto Deploy Test - 2025년 12월 17일 수 오후  4:59:40

# Auto Deploy Test 2

# Deploy Test - IP Changed

# test2
# test3
# test4
