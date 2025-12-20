# 프론트엔드 API 연동 가이드

백엔드 준비 완료! 프론트엔드에서 아래 API들을 바로 호출하면 됩니다.

## 서버 정보

- **백엔드**: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`

## API 목록

### 1. 스토리 생성

#### 1-1. 소설 업로드 & 분석 시작
```http
POST /api/stories/upload
Content-Type: application/json

{
  "title": "내 소설 제목",
  "genre": "판타지",
  "novelText": "소설 내용..."
}
```

**응답**
```json
{
  "storyId": "story_abc123",
  "title": "내 소설 제목",
  "genre": "판타지",
  "status": "ANALYZING",
  "createdAt": "2025-12-15T10:30:00"
}
```

#### 1-2. 분석 요약 조회
```http
GET /api/stories/{storyId}/summary
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "GAUGES_READY",
  "summary": "소설 요약 내용..."
}
```

#### 1-3. 캐릭터 정보 조회
```http
GET /api/stories/{storyId}/characters
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "GAUGES_READY",
  "characters": [
    {
      "id": "char_1",
      "name": "홍길동",
      "description": "주인공",
      "personality": "용감한"
    }
  ]
}
```

#### 1-4. 게이지 제안 조회
```http
GET /api/stories/{storyId}/gauges
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "GAUGES_READY",
  "gauges": [
    {
      "id": "gauge_1",
      "name": "도덕성",
      "description": "선과 악의 균형"
    },
    {
      "id": "gauge_2",
      "name": "용기",
      "description": "두려움을 극복하는 힘"
    }
  ]
}
```

#### 1-5. 게이지 선택 (2개)
```http
POST /api/stories/{storyId}/gauges/select
Content-Type: application/json

{
  "selectedGaugeIds": ["gauge_1", "gauge_2"]
}
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "GAUGES_SELECTED",
  "selectedGauges": [...]
}
```

#### 1-6. 스토리 설정
```http
POST /api/stories/{storyId}/config
Content-Type: application/json

{
  "description": "스토리 설명",
  "numEpisodes": 5,
  "maxDepth": 3,
  "numEpisodeEndings": 2
}
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "CONFIGURED",
  "config": {
    "description": "스토리 설명",
    "numEpisodes": 5,
    "maxDepth": 3,
    "numEpisodeEndings": 2
  }
}
```

#### 1-7. 에피소드 1 생성 시작
```http
POST /api/stories/{storyId}/generate
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "GENERATING",
  "episode": {
    "order": 1,
    "title": "첫 번째 에피소드",
    "introText": "에피소드 시작 텍스트",
    "nodes": [...]
  }
}
```

#### 1-8. 다음 에피소드 생성
```http
POST /api/stories/{storyId}/generate-next
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "GENERATING",
  "episode": {
    "order": 2,
    "title": "두 번째 에피소드",
    ...
  }
}
```

#### 1-9. 생성 진행 상황 확인
```http
GET /api/stories/{storyId}/progress
```

**응답**
```json
{
  "storyId": "story_abc123",
  "status": "GENERATING",
  "progress": {
    "currentPhase": "GENERATING_EPISODE_2",
    "completedEpisodes": 1,
    "totalEpisodes": 5,
    "percentage": 20,
    "message": "에피소드 2 생성 중...",
    "error": null
  }
}
```

---

### 2. 게임 플레이

#### 2-1. 게임 시작
```http
POST /api/game/start
Content-Type: application/json

{
  "storyDataId": "story_data_123",
  "userId": "user_456"
}
```

**응답**
```json
{
  "sessionId": "session_789",
  "storyId": "story_data_123",
  "currentEpisode": 1,
  "currentNode": {
    "nodeId": "node_1",
    "text": "당신은 숲 속에 있습니다...",
    "imageUrl": "https://...",
    "choices": [
      {
        "choiceId": "choice_1",
        "text": "숲 깊이 들어간다",
        "nextNodeId": "node_2"
      },
      {
        "choiceId": "choice_2",
        "text": "돌아간다",
        "nextNodeId": "node_3"
      }
    ]
  },
  "gauges": {
    "도덕성": 50,
    "용기": 50
  }
}
```

#### 2-2. 선택지 선택
```http
POST /api/game/{sessionId}/choice
Content-Type: application/json

{
  "choiceId": "choice_1"
}
```

**응답**
```json
{
  "sessionId": "session_789",
  "currentNode": {
    "nodeId": "node_2",
    "text": "숲 깊이 들어가자...",
    ...
  },
  "gauges": {
    "도덕성": 45,
    "용기": 55
  },
  "tags": ["brave", "adventurous"]
}
```

#### 2-3. 게임 상태 조회
```http
GET /api/game/{sessionId}
```

**응답**
```json
{
  "sessionId": "session_789",
  "storyId": "story_data_123",
  "currentEpisode": 1,
  "currentNode": {...},
  "gauges": {...},
  "tags": [...]
}
```

---

### 3. RAG 챗봇 (캐릭터와 대화)

#### 3-1. 캐릭터 학습 (인덱싱)
```http
POST /api/rag/index-character
Content-Type: application/json

{
  "characterId": "char_001",
  "name": "홍길동",
  "description": "주인공 캐릭터",
  "personality": "용감하고 정의로운",
  "background": "어릴 적 부모를 잃고...",
  "dialogueSamples": [
    "나는 정의를 실현하겠다",
    "악은 용서할 수 없어"
  ],
  "relationships": {
    "김철수": "친구",
    "이영희": "연인"
  }
}
```

**응답**
```json
true  // 성공
```

#### 3-2. 캐릭터와 채팅
```http
POST /api/rag/chat
Content-Type: application/json

{
  "characterId": "char_001",
  "userMessage": "안녕하세요, 당신은 누구인가요?",
  "conversationHistory": [],
  "maxTokens": 500
}
```

**응답**
```json
{
  "characterId": "char_001",
  "aiMessage": "안녕하십니까. 저는 홍길동입니다. 정의를 실현하기 위해 싸우는 사람이죠.",
  "sources": [
    {
      "text": "주인공 캐릭터",
      "score": 0.95,
      "sourceType": "description"
    }
  ],
  "timestamp": "2025-12-15T10:30:00"
}
```

#### 3-3. 대화 이력 포함 채팅
```http
POST /api/rag/chat
Content-Type: application/json

{
  "characterId": "char_001",
  "userMessage": "당신의 목표는 무엇인가요?",
  "conversationHistory": [
    {
      "role": "user",
      "content": "안녕하세요, 당신은 누구인가요?"
    },
    {
      "role": "assistant",
      "content": "안녕하십니까. 저는 홍길동입니다..."
    }
  ],
  "maxTokens": 500
}
```

---

### 4. 커뮤니티 (게시글/댓글/리뷰)

#### 4-1. 게시글 목록 조회
```http
GET /api/posts?page=0&size=20
```

#### 4-2. 게시글 작성
```http
POST /api/posts
Content-Type: application/json
Authorization: Bearer {accessToken}

{
  "title": "게시글 제목",
  "content": "게시글 내용"
}
```

#### 4-3. 리뷰 작성
```http
POST /api/reviews
Content-Type: application/json
Authorization: Bearer {accessToken}

{
  "storyDataId": "story_data_123",
  "rating": 5,
  "content": "정말 재미있었습니다!"
}
```

---

## 인증 (JWT)

### 1. 회원가입
```http
POST /api/auth/signup
Content-Type: application/json

{
  "username": "user123",
  "password": "password123",
  "email": "user@example.com"
}
```

### 2. 로그인
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "user123",
  "password": "password123"
}
```

**응답**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### 3. 인증이 필요한 API 호출
```http
GET /api/user/profile
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

---

## 에러 응답

모든 API는 에러 시 다음 형식으로 응답합니다:

```json
{
  "error": "BAD_REQUEST",
  "message": "Invalid request parameters",
  "timestamp": "2025-12-15T10:30:00"
}
```

---

## 상태 코드

- `200 OK`: 성공
- `201 Created`: 생성 성공
- `400 Bad Request`: 잘못된 요청
- `401 Unauthorized`: 인증 필요
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스 없음
- `500 Internal Server Error`: 서버 오류

---

## 개발 팁

1. **Swagger UI 활용**: `http://localhost:8080/swagger-ui.html`에서 모든 API를 테스트할 수 있습니다
2. **CORS 설정**: 로컬 개발 시 `http://localhost:3000`, `http://localhost:5173`이 허용되어 있습니다
3. **타임아웃**: 스토리 생성 API는 시간이 오래 걸릴 수 있으니 타임아웃을 충분히 설정하세요 (권장: 10분)
4. **폴링**: 생성 진행 상황은 `/progress` 엔드포인트를 3-5초마다 폴링하세요

---

## 환경별 URL

| 환경 | URL |
|------|-----|
| 로컬 개발 | `http://localhost:8080` |
| 개발 서버 | `http://dev-api.yourdomain.com` |
| 운영 서버 | `https://api.yourdomain.com` |

---

## 문제 해결

### 1. CORS 에러
- 백엔드 `.env` 파일에서 `CORS_ALLOWED_ORIGINS`에 프론트엔드 URL 추가

### 2. 타임아웃 에러
- 스토리 생성 시 fetch timeout을 10분으로 설정

### 3. 401 Unauthorized
- 로그인 후 받은 `accessToken`을 `Authorization: Bearer {token}` 헤더에 포함

---

백엔드는 완전히 준비되었습니다! 프론트엔드에서 위 API들을 호출하기만 하면 됩니다.
