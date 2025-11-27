# 스토리 생성 API 문서

이 문서는 소설을 업로드하고 AI를 통해 인터랙티브 게임 스토리를 생성하는 API를 설명합니다.

## 목차
1. [전체 플로우](#전체-플로우)
2. [API 엔드포인트](#api-엔드포인트)
3. [상세 설명](#상세-설명)
4. [예제](#예제)

---

## 전체 플로우

### 방법 1: 직접 업로드 (작은 텍스트용)

```
1. 소설 업로드 및 분석 시작
   POST /api/stories/upload

2. 요약 조회
   GET /api/stories/{id}/summary

3. 캐릭터 조회
   GET /api/stories/{id}/characters

4. 게이지 제안 조회
   GET /api/stories/{id}/gauges

5. 게이지 선택
   POST /api/stories/{id}/gauges/select

6. 생성 설정
   POST /api/stories/{id}/config

7. 스토리 생성 시작
   POST /api/stories/{id}/generate

8. 생성 진행률 조회 (폴링)
   GET /api/stories/{id}/progress

9. 생성 완료 결과 조회
   GET /api/stories/{id}/result

10. 전체 스토리 데이터 조회
    GET /api/stories/{id}/data
```

### 방법 2: S3 업로드 (큰 파일용) 🆕

```
1. Pre-signed URL 요청
   GET /api/upload/presigned-url?fileName=novel.txt

2. S3에 파일 직접 업로드
   PUT {uploadUrl} (프론트엔드에서 직접)

3. S3에서 소설 읽어서 분석 시작
   POST /api/stories/upload-from-s3

4. 요약 조회
   GET /api/stories/{id}/summary

5. 캐릭터 조회
   GET /api/stories/{id}/characters

6. 게이지 제안 조회
   GET /api/stories/{id}/gauges

7. 게이지 선택
   POST /api/stories/{id}/gauges/select

8. 생성 설정
   POST /api/stories/{id}/config

9. 스토리 생성 시작
   POST /api/stories/{id}/generate

10. 생성 진행률 조회 (폴링)
    GET /api/stories/{id}/progress

11. 생성 완료 결과 조회
    GET /api/stories/{id}/result

12. 전체 스토리 데이터 조회
    GET /api/stories/{id}/data
```

---

## API 엔드포인트

### 1. 소설 업로드 및 분석 시작

**요청**
```http
POST /api/stories/upload
Content-Type: application/json

{
  "title": "파리대왕",
  "novelText": "무인도에 고립된 소년들의 이야기..."
}
```

**응답**
```json
{
  "storyId": "story_123",
  "title": "파리대왕",
  "status": "ANALYZING",
  "createdAt": "2025-11-24T10:00:00"
}
```

**설명**
- 소설을 업로드하고 AI 서버에 분석을 요청합니다
- 백그라운드에서 AI 분석이 시작됩니다 (요약, 캐릭터, 게이지 제안)
- `storyId`를 반환하며, 이후 모든 API에서 사용됩니다

---

### 2. 요약 조회

**요청**
```http
GET /api/stories/{storyId}/summary
```

**응답**
```json
{
  "storyId": "story_123",
  "status": "ANALYZING" | "SUMMARY_READY",
  "summary": "무인도에 고립된 소년들이 문명에서 야만으로 퇴행하는 이야기. 랄프와 잭의 대립을 통해 인간 본성의 어두운 면을 탐구한다."
}
```

**설명**
- AI가 생성한 500자 요약을 조회합니다
- `status`가 `SUMMARY_READY`가 될 때까지 폴링합니다

---

### 3. 캐릭터 조회

**요청**
```http
GET /api/stories/{storyId}/characters
```

**응답**
```json
{
  "storyId": "story_123",
  "status": "ANALYZING" | "CHARACTERS_READY",
  "characters": [
    {
      "name": "랄프",
      "aliases": ["리더", "소라고동을 가진 소년"],
      "description": "민주적 리더십을 가진 소년. 이성적이고 책임감이 강하다.",
      "relationships": ["잭과 대립", "피기의 친구"]
    },
    {
      "name": "잭",
      "aliases": ["사냥꾼 대장"],
      "description": "권위주의적이고 폭력적인 성향. 점차 야만화된다.",
      "relationships": ["랄프와 대립", "사냥꾼들의 리더"]
    },
    {
      "name": "피기",
      "aliases": ["안경 쓴 소년"],
      "description": "지적이고 논리적이지만 신체적으로 약하다.",
      "relationships": ["랄프의 조언자"]
    },
    {
      "name": "사이먼",
      "aliases": ["신비로운 소년"],
      "description": "영적이고 순수한 소년. 진실을 깨닫는다.",
      "relationships": ["고립된 존재"]
    }
  ]
}
```

**설명**
- AI가 추출한 주요 캐릭터 목록을 조회합니다
- 각 캐릭터의 이름, 별칭, 설명, 관계를 포함합니다

---

### 4. 게이지 제안 조회

**요청**
```http
GET /api/stories/{storyId}/gauges
```

**응답**
```json
{
  "storyId": "story_123",
  "status": "ANALYZING" | "GAUGES_READY",
  "gauges": [
    {
      "id": "civilization",
      "name": "문명",
      "meaning": "소년들의 문명 수준",
      "minLabel": "야만",
      "maxLabel": "문명",
      "description": "이성과 질서 vs 본능과 혼돈"
    },
    {
      "id": "hope",
      "name": "희망",
      "meaning": "구조에 대한 희망",
      "minLabel": "절망",
      "maxLabel": "희망",
      "description": "구조될 것이라는 믿음"
    },
    {
      "id": "unity",
      "name": "단결",
      "meaning": "소년들 간의 단합",
      "minLabel": "분열",
      "maxLabel": "단결",
      "description": "집단의 화합 정도"
    },
    {
      "id": "rationality",
      "name": "이성",
      "meaning": "이성적 판단 능력",
      "minLabel": "본능",
      "maxLabel": "이성",
      "description": "논리적 사고 vs 감정적 반응"
    },
    {
      "id": "trust",
      "name": "신뢰",
      "meaning": "서로에 대한 믿음",
      "minLabel": "불신",
      "maxLabel": "신뢰",
      "description": "구성원 간의 신뢰도"
    }
  ]
}
```

**설명**
- AI가 소설 주제에 맞춰 제안한 5개의 게이지를 조회합니다
- **사용자는 이 중 2개를 선택해야 합니다**

---

### 5. 게이지 선택

**요청**
```http
POST /api/stories/{storyId}/gauges/select
Content-Type: application/json

{
  "selectedGaugeIds": ["civilization", "unity"]
}
```

**응답**
```json
{
  "storyId": "story_123",
  "status": "GAUGES_SELECTED",
  "selectedGauges": [
    {
      "id": "civilization",
      "name": "문명",
      "meaning": "소년들의 문명 수준",
      "minLabel": "야만",
      "maxLabel": "문명",
      "description": "이성과 질서 vs 본능과 혼돈"
    },
    {
      "id": "unity",
      "name": "단결",
      "meaning": "소년들 간의 단합",
      "minLabel": "분열",
      "maxLabel": "단결",
      "description": "집단의 화합 정도"
    }
  ]
}
```

**설명**
- 사용자가 선택한 2개의 게이지를 저장합니다
- 선택된 게이지는 게임 플레이 중 추적됩니다

---

### 6. 생성 설정

**요청**
```http
POST /api/stories/{storyId}/config
Content-Type: application/json

{
  "description": "문명과 야만 사이의 선택",
  "numEpisodes": 3,
  "maxDepth": 3,
  "endingConfig": {
    "happy": 2,
    "tragic": 1,
    "neutral": 1,
    "open": 1,
    "bad": 0,
    "bittersweet": 0
  },
  "numEpisodeEndings": 3
}
```

**응답**
```json
{
  "storyId": "story_123",
  "status": "CONFIGURED",
  "config": {
    "description": "문명과 야만 사이의 선택",
    "numEpisodes": 3,
    "maxDepth": 3,
    "endingConfig": {
      "happy": 2,
      "tragic": 1,
      "neutral": 1,
      "open": 1,
      "bad": 0,
      "bittersweet": 0
    },
    "numEpisodeEndings": 3
  }
}
```

**설명**
- 스토리 생성 설정을 저장합니다
- **numEpisodes**: 에피소드 수 (1-10)
- **maxDepth**: 스토리 트리 깊이 (2-5)
- **endingConfig**: 최종 엔딩 타입 분포
  - happy: 행복한 엔딩
  - tragic: 비극적 엔딩
  - neutral: 중립적 엔딩
  - open: 열린 엔딩
  - bad: 배드 엔딩
  - bittersweet: 씁쓸한 엔딩
- **numEpisodeEndings**: 각 에피소드별 엔딩 수 (1-5)

---

### 7. 스토리 생성 시작

**요청**
```http
POST /api/stories/{storyId}/generate
```

**응답**
```json
{
  "storyId": "story_123",
  "status": "GENERATING",
  "message": "Story generation started",
  "estimatedTime": "5-10 minutes"
}
```

**설명**
- AI 서버에 스토리 생성을 요청합니다
- 백그라운드에서 생성이 진행됩니다
- 생성 시간은 설정에 따라 5-10분 소요됩니다

---

### 8. 생성 진행률 조회

**요청**
```http
GET /api/stories/{storyId}/progress
```

**응답 (진행 중)**
```json
{
  "storyId": "story_123",
  "status": "GENERATING",
  "progress": {
    "currentPhase": "EPISODE_GENERATION",
    "completedEpisodes": 1,
    "totalEpisodes": 3,
    "percentage": 33,
    "message": "Generating episode 2 of 3..."
  }
}
```

**응답 (완료)**
```json
{
  "storyId": "story_123",
  "status": "COMPLETED",
  "progress": {
    "currentPhase": "COMPLETED",
    "completedEpisodes": 3,
    "totalEpisodes": 3,
    "percentage": 100,
    "message": "Story generation completed"
  }
}
```

**응답 (실패)**
```json
{
  "storyId": "story_123",
  "status": "FAILED",
  "progress": {
    "currentPhase": "FAILED",
    "percentage": 0,
    "message": "AI server error: timeout",
    "error": "Generation timeout after 10 minutes"
  }
}
```

**설명**
- 스토리 생성 진행 상태를 조회합니다
- 프론트엔드에서 3-5초마다 폴링하여 진행률을 표시합니다
- **Phase 종류**:
  - ANALYZING: 소설 분석 중
  - FINAL_ENDINGS: 최종 엔딩 생성 중
  - EPISODE_GENERATION: 에피소드 생성 중
  - COMPLETED: 완료
  - FAILED: 실패

---

### 9. 생성 완료 결과 조회

**요청**
```http
GET /api/stories/{storyId}/result
```

**응답**
```json
{
  "storyId": "story_123",
  "status": "COMPLETED",
  "storyDataId": 456,
  "metadata": {
    "title": "파리대왕: 무인도의 선택",
    "description": "문명과 야만 사이의 선택",
    "totalEpisodes": 3,
    "totalNodes": 40,
    "totalGauges": 2,
    "createdAt": "2025-11-24T10:15:00"
  },
  "preview": {
    "firstEpisodeTitle": "첫 날 밤",
    "firstEpisodeIntro": "비행기 추락 후, 소년들은 무인도 해변에 모였다...",
    "selectedGauges": [
      {
        "id": "civilization",
        "name": "문명",
        "minLabel": "야만",
        "maxLabel": "문명"
      },
      {
        "id": "unity",
        "name": "단결",
        "minLabel": "분열",
        "maxLabel": "단결"
      }
    ]
  }
}
```

**설명**
- 생성 완료된 스토리의 정보를 조회합니다
- **storyDataId**: 게임 플레이 시 사용할 스토리 ID
- preview만 포함 (전체 데이터는 `/data` 엔드포인트 사용)

---

### 10. 전체 스토리 데이터 조회 🆕

**요청**
```http
GET /api/stories/{storyId}/data
```

**응답**
```json
{
  "metadata": {
    "totalEpisodes": 3,
    "totalNodes": 40,
    "totalGauges": 2,
    "totalCharacters": 4
  },
  "context": {
    "summary": "무인도에 고립된 소년들이...",
    "characters": [
      {
        "name": "랄프",
        "description": "민주적 리더",
        "relationships": ["잭과 대립"]
      }
    ],
    "selectedGauges": [
      {
        "id": "civilization",
        "name": "문명",
        "minLabel": "야만",
        "maxLabel": "문명"
      }
    ],
    "finalEndings": [
      {
        "id": "ending_happy_1",
        "type": "happy",
        "title": "구조와 귀환",
        "condition": "civilization >= 70 AND unity >= 60",
        "narrative": "소년들은 질서를 유지하며..."
      }
    ]
  },
  "episodes": [
    {
      "id": "ep1",
      "order": 1,
      "title": "첫 날 밤",
      "introText": "비행기 추락 후...",
      "nodes": [
        {
          "id": "ep1_node_0",
          "depth": 0,
          "text": "랄프가 제안한다...",
          "choices": [
            {
              "text": "랄프를 지지한다",
              "tags": ["cooperative", "rational"]
            }
          ]
        }
      ],
      "endings": [
        {
          "id": "ep1_ending_1",
          "title": "성공적인 첫 날",
          "condition": "cooperative >= 2",
          "narrative": "신호불이 올라간다...",
          "gaugeChanges": {
            "civilization": 15,
            "unity": 10
          }
        }
      ]
    }
  ]
}
```

**설명**
- **생성 완료된 전체 스토리 JSON을 반환합니다**
- 프론트엔드에서 게임을 구성하는 데 사용됩니다
- 모든 에피소드, 노드, 선택지, 엔딩 정보 포함
- **크기가 클 수 있으니 주의** (수백 KB ~ MB)

**프론트엔드 사용 예:**
```typescript
// 1. 생성 완료 확인
const result = await GET(`/api/stories/${storyId}/result`);

// 2. 전체 데이터 로드 (게임 구성)
const fullStory = await GET(`/api/stories/${storyId}/data`);

// 3. GameStateManager 초기화
gameState.loadStory(fullStory);
```

**대안 엔드포인트:**
```
GET /api/game/stories/{storyDataId}/data
→ storyDataId로 직접 조회 가능
```

---

## 상태(Status) 종류

| Status | 설명 |
|--------|------|
| `ANALYZING` | 소설 분석 중 (요약, 캐릭터, 게이지 추출) |
| `SUMMARY_READY` | 요약 생성 완료 |
| `CHARACTERS_READY` | 캐릭터 추출 완료 |
| `GAUGES_READY` | 게이지 제안 완료 |
| `GAUGES_SELECTED` | 사용자가 게이지 선택 완료 |
| `CONFIGURED` | 생성 설정 완료 |
| `GENERATING` | 스토리 생성 중 |
| `COMPLETED` | 생성 완료 |
| `FAILED` | 생성 실패 |

---

## 예제: 전체 플로우

### Step 1: 소설 업로드
```bash
curl -X POST http://localhost:8080/api/stories/upload \
  -H "Content-Type: application/json" \
  -d '{
    "title": "파리대왕",
    "novelText": "무인도에 고립된 소년들..."
  }'

# 응답
{
  "storyId": "story_123",
  "status": "ANALYZING"
}
```

### Step 2: 요약 조회 (폴링)
```bash
curl http://localhost:8080/api/stories/story_123/summary

# 응답 (분석 중)
{
  "status": "ANALYZING"
}

# 3초 후 재시도
{
  "status": "SUMMARY_READY",
  "summary": "무인도에 고립된 소년들이..."
}
```

### Step 3: 캐릭터 조회
```bash
curl http://localhost:8080/api/stories/story_123/characters

# 응답
{
  "status": "CHARACTERS_READY",
  "characters": [...]
}
```

### Step 4: 게이지 조회
```bash
curl http://localhost:8080/api/stories/story_123/gauges

# 응답
{
  "status": "GAUGES_READY",
  "gauges": [5개 게이지]
}
```

### Step 5: 게이지 선택
```bash
curl -X POST http://localhost:8080/api/stories/story_123/gauges/select \
  -H "Content-Type: application/json" \
  -d '{
    "selectedGaugeIds": ["civilization", "unity"]
  }'

# 응답
{
  "status": "GAUGES_SELECTED",
  "selectedGauges": [2개]
}
```

### Step 6: 생성 설정
```bash
curl -X POST http://localhost:8080/api/stories/story_123/config \
  -H "Content-Type: application/json" \
  -d '{
    "description": "문명과 야만 사이의 선택",
    "numEpisodes": 3,
    "maxDepth": 3,
    "endingConfig": {
      "happy": 2,
      "tragic": 1,
      "neutral": 1,
      "open": 1
    },
    "numEpisodeEndings": 3
  }'

# 응답
{
  "status": "CONFIGURED"
}
```

### Step 7: 생성 시작
```bash
curl -X POST http://localhost:8080/api/stories/story_123/generate

# 응답
{
  "status": "GENERATING",
  "estimatedTime": "5-10 minutes"
}
```

### Step 8: 진행률 조회 (폴링)
```bash
curl http://localhost:8080/api/stories/story_123/progress

# 응답 (30초 후)
{
  "status": "GENERATING",
  "progress": {
    "percentage": 10,
    "message": "Generating final endings..."
  }
}

# 2분 후
{
  "status": "GENERATING",
  "progress": {
    "percentage": 33,
    "completedEpisodes": 1,
    "message": "Generating episode 2 of 3..."
  }
}

# 5분 후
{
  "status": "COMPLETED",
  "progress": {
    "percentage": 100,
    "message": "Story generation completed"
  }
}
```

### Step 9: 결과 조회
```bash
curl http://localhost:8080/api/stories/story_123/result

# 응답
{
  "status": "COMPLETED",
  "storyDataId": 456,
  "metadata": {
    "totalEpisodes": 3,
    "totalNodes": 40
  }
}
```

### Step 10: 게임 시작
```bash
curl -X POST http://localhost:8080/api/game/start \
  -H "Content-Type: application/json" \
  -d '{
    "storyDataId": 456
  }'
```

---

## 프론트엔드 UI 예시

### 1. 소설 업로드 화면
```
┌────────────────────────────────────────┐
│ 새 스토리 생성                         │
├────────────────────────────────────────┤
│ 제목: [파리대왕               ]        │
│                                        │
│ 소설 텍스트:                           │
│ ┌────────────────────────────────────┐ │
│ │무인도에 고립된 소년들...           │ │
│ │                                    │ │
│ └────────────────────────────────────┘ │
│                                        │
│              [업로드 및 분석 시작]      │
└────────────────────────────────────────┘
```

### 2. 분석 진행 화면
```
┌────────────────────────────────────────┐
│ 소설 분석 중...                        │
├────────────────────────────────────────┤
│ ✓ 요약 생성 완료                       │
│ ✓ 캐릭터 추출 완료                     │
│ ⏳ 게이지 시스템 설계 중...            │
│                                        │
│ [████████░░░░░░░░] 60%                │
└────────────────────────────────────────┘
```

### 3. 게이지 선택 화면
```
┌────────────────────────────────────────┐
│ 게이지 선택 (2개 선택)                 │
├────────────────────────────────────────┤
│ ☑ 문명 (야만 ←→ 문명)                 │
│   이성과 질서 vs 본능과 혼돈           │
│                                        │
│ ☐ 희망 (절망 ←→ 희망)                 │
│   구조될 것이라는 믿음                 │
│                                        │
│ ☑ 단결 (분열 ←→ 단결)                 │
│   집단의 화합 정도                     │
│                                        │
│              [다음 단계]                │
└────────────────────────────────────────┘
```

### 4. 생성 설정 화면
```
┌────────────────────────────────────────┐
│ 스토리 생성 설정                       │
├────────────────────────────────────────┤
│ 에피소드 수: [3] ▼                     │
│ 스토리 깊이: [3] ▼                     │
│                                        │
│ 최종 엔딩 분포 (총 5개):               │
│   행복한 엔딩: [2]                     │
│   비극적 엔딩: [1]                     │
│   중립적 엔딩: [1]                     │
│   열린 엔딩:   [1]                     │
│                                        │
│              [생성 시작]                │
└────────────────────────────────────────┘
```

### 5. 생성 진행 화면
```
┌────────────────────────────────────────┐
│ 스토리 생성 중...                      │
├────────────────────────────────────────┤
│ 에피소드 2 / 3 생성 중                 │
│                                        │
│ [████████████░░░░] 66%                │
│                                        │
│ 예상 남은 시간: 2분                    │
│                                        │
│ 💡 AI가 40개의 스토리 노드를           │
│    생성하고 있습니다...                │
└────────────────────────────────────────┘
```

### 6. 생성 완료 화면
```
┌────────────────────────────────────────┐
│ ✓ 스토리 생성 완료!                    │
├────────────────────────────────────────┤
│ 파리대왕: 무인도의 선택                │
│                                        │
│ • 총 3개 에피소드                      │
│ • 40개 스토리 노드                     │
│ • 5개 최종 엔딩                        │
│                                        │
│ 첫 에피소드: "첫 날 밤"                │
│ "비행기 추락 후, 소년들은..."          │
│                                        │
│    [게임 시작하기]  [목록으로]         │
└────────────────────────────────────────┘
```

---

## 에러 처리

### 400 Bad Request
```json
{
  "error": "INVALID_REQUEST",
  "message": "Must select exactly 2 gauges",
  "details": {
    "selectedCount": 3,
    "requiredCount": 2
  }
}
```

### 404 Not Found
```json
{
  "error": "STORY_NOT_FOUND",
  "message": "Story with id 'story_123' not found"
}
```

### 409 Conflict
```json
{
  "error": "INVALID_STATE",
  "message": "Cannot generate story: gauges not selected",
  "currentStatus": "GAUGES_READY",
  "requiredStatus": "GAUGES_SELECTED"
}
```

### 500 Internal Server Error
```json
{
  "error": "AI_SERVER_ERROR",
  "message": "AI server timeout",
  "details": {
    "phase": "EPISODE_GENERATION",
    "retryable": true
  }
}
```

---

## S3 파일 업로드 가이드 🆕

### 언제 S3를 사용하나요?

| 방법 | 사용 시기 | 장점 | 단점 |
|------|----------|------|------|
| **직접 업로드** | 작은 텍스트 (< 1MB) | 간단, 빠름 | 서버 부하 |
| **S3 업로드** | 큰 파일 (> 1MB) | 서버 부하 ↓, 진행률 표시 | 복잡, AWS 필요 |

### S3 업로드 사용법

#### 1. Pre-signed URL 요청

```http
GET /api/upload/presigned-url?fileName=my-novel.txt
```

**응답:**
```json
{
  "uploadUrl": "https://story-game-bucket.s3.ap-northeast-2.amazonaws.com/uploads/abc123_my-novel.txt?...",
  "fileKey": "uploads/abc123_my-novel.txt",
  "expiresIn": 900,
  "method": "PUT"
}
```

#### 2. S3에 파일 직접 업로드 (프론트엔드)

```javascript
// JavaScript/TypeScript 예시
const file = document.getElementById('fileInput').files[0];

// Pre-signed URL 요청
const { uploadUrl, fileKey } = await fetch(
  `/api/upload/presigned-url?fileName=${encodeURIComponent(file.name)}`
).then(r => r.json());

// S3에 직접 업로드
await fetch(uploadUrl, {
  method: 'PUT',
  body: file,
  headers: {
    'Content-Type': 'text/plain'
  }
});

console.log('Upload complete! FileKey:', fileKey);
```

**업로드 진행률 표시:**
```javascript
const xhr = new XMLHttpRequest();

xhr.upload.addEventListener('progress', (e) => {
  if (e.lengthComputable) {
    const percentComplete = (e.loaded / e.total) * 100;
    console.log(`Upload: ${percentComplete}%`);
  }
});

xhr.open('PUT', uploadUrl);
xhr.setRequestHeader('Content-Type', 'text/plain');
xhr.send(file);
```

#### 3. 업로드 완료 후 분석 시작

```http
POST /api/stories/upload-from-s3
Content-Type: application/json

{
  "title": "파리대왕",
  "description": "무인도 생존 이야기",
  "fileKey": "uploads/abc123_my-novel.txt"
}
```

**응답:**
```json
{
  "storyId": "story_456",
  "title": "파리대왕",
  "status": "ANALYZING",
  "createdAt": "2025-11-24T15:00:00"
}
```

### React/SvelteKit 예시

**React:**
```tsx
import { useState } from 'react';

function NovelUpload() {
  const [progress, setProgress] = useState(0);

  const handleUpload = async (file: File) => {
    // 1. Pre-signed URL 요청
    const { uploadUrl, fileKey } = await fetch(
      `/api/upload/presigned-url?fileName=${file.name}`
    ).then(r => r.json());

    // 2. S3 업로드 (진행률 추적)
    const xhr = new XMLHttpRequest();

    xhr.upload.onprogress = (e) => {
      setProgress((e.loaded / e.total) * 100);
    };

    await new Promise((resolve, reject) => {
      xhr.onload = resolve;
      xhr.onerror = reject;
      xhr.open('PUT', uploadUrl);
      xhr.send(file);
    });

    // 3. 분석 시작
    const response = await fetch('/api/stories/upload-from-s3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        title: '내 소설',
        fileKey
      })
    }).then(r => r.json());

    console.log('Story created:', response.storyId);
  };

  return (
    <div>
      <input type="file" onChange={(e) => handleUpload(e.target.files[0])} />
      <progress value={progress} max={100} />
    </div>
  );
}
```

**SvelteKit:**
```svelte
<script lang="ts">
  let progress = 0;

  async function handleUpload(file: File) {
    // 1. Pre-signed URL 요청
    const { uploadUrl, fileKey } = await fetch(
      `/api/upload/presigned-url?fileName=${file.name}`
    ).then(r => r.json());

    // 2. S3 업로드
    const xhr = new XMLHttpRequest();
    xhr.upload.onprogress = (e) => {
      progress = (e.loaded / e.total) * 100;
    };

    await new Promise((resolve, reject) => {
      xhr.onload = resolve;
      xhr.onerror = reject;
      xhr.open('PUT', uploadUrl);
      xhr.send(file);
    });

    // 3. 분석 시작
    const response = await fetch('/api/stories/upload-from-s3', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: '내 소설', fileKey })
    }).then(r => r.json());

    console.log('Story created:', response.storyId);
  }
</script>

<input type="file" on:change={(e) => handleUpload(e.target.files[0])} />
<progress value={progress} max={100}></progress>
```

### AWS 설정

**환경 변수 설정:**
```bash
export AWS_S3_BUCKET=story-game-bucket
export AWS_S3_REGION=ap-northeast-2
export AWS_ACCESS_KEY=your-access-key
export AWS_SECRET_KEY=your-secret-key
```

**S3 버킷 CORS 설정:**
```json
[
  {
    "AllowedHeaders": ["*"],
    "AllowedMethods": ["PUT", "GET"],
    "AllowedOrigins": ["http://localhost:3000", "http://localhost:5173"],
    "ExposeHeaders": []
  }
]
```

---

## 다음 단계

스토리 생성이 완료되면 **게임 플레이 API**를 사용합니다:
- [게임 플레이 API 문서](GAMEPLAY_API.md) (추후 작성 예정)

