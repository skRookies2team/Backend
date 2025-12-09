# 최종 엔딩 전용 API 가이드

## 새로운 API 엔드포인트

게임 완료 후 최종 엔딩 정보만 조회하는 전용 API가 추가되었습니다.

---

## API 명세

### GET /api/game/{sessionId}/ending

게임이 완료된 세션의 최종 엔딩 정보를 조회합니다.

**Authorization**: JWT 토큰 필요

#### Request

```http
GET /api/game/{sessionId}/ending HTTP/1.1
Host: localhost:8080
Authorization: Bearer {jwt_token}
```

**Path Parameters**:
- `sessionId` (string, required): 게임 세션 ID

#### Response

**성공 (200 OK)**:

```json
{
  "sessionId": "session_abc123",
  "isCompleted": true,
  "finalEnding": {
    "id": "ending_happy",
    "type": "HAPPY",
    "title": "행복한 결말",
    "condition": "#trust >= 70 AND #courage >= 60",
    "summary": "당신의 용기와 신뢰가 모두를 구했습니다. 세상은 다시 평화를 되찾았고, 당신은 영웅으로 기억될 것입니다."
  },
  "finalGaugeStates": {
    "trust": 75,
    "courage": 65
  },
  "gaugeDefinitions": [
    {
      "id": "trust",
      "name": "신뢰",
      "description": "동료와의 신뢰 관계",
      "icon": "🤝"
    },
    {
      "id": "courage",
      "name": "용기",
      "description": "위험을 감수하는 용기",
      "icon": "⚔️"
    }
  ],
  "completedEpisodesCount": 5
}
```

**에러 응답**:

1. **게임이 완료되지 않음 (400 Bad Request)**:
```json
{
  "error": "Game is not completed yet. sessionId: session_abc123"
}
```

2. **세션을 찾을 수 없음 (404 Not Found)**:
```json
{
  "error": "Session not found: session_abc123"
}
```

3. **권한 없음 (403 Forbidden)**:
```json
{
  "error": "Unauthorized: You don't have permission to access this game session"
}
```

---

## 응답 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| `sessionId` | String | 게임 세션 ID |
| `isCompleted` | Boolean | 게임 완료 여부 (항상 true) |
| `finalEnding` | FinalEndingDto | 최종 엔딩 상세 정보 (조건 불일치 시 null 가능) |
| `finalGaugeStates` | Map<String, Integer> | 게임 종료 시점의 최종 게이지 상태 (0-100) |
| `gaugeDefinitions` | List<GaugeDto> | 게이지 정의 목록 (UI 표시용) |
| `completedEpisodesCount` | Integer | 완료한 에피소드 수 |

### FinalEndingDto 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | String | 엔딩 고유 ID (예: "ending_happy") |
| `type` | String | 엔딩 타입 (HAPPY, BAD, NEUTRAL 등) |
| `title` | String | 엔딩 제목 |
| `condition` | String | 엔딩 조건 (SpEL 표현식) |
| `summary` | String | 엔딩 본문 (스토리 설명) |

---

## 사용 예시

### 1. JavaScript/TypeScript

```typescript
interface FinalEndingResponse {
  sessionId: string;
  isCompleted: boolean;
  finalEnding?: {
    id: string;
    type: string;
    title: string;
    condition: string;
    summary: string;
  };
  finalGaugeStates: Record<string, number>;
  gaugeDefinitions?: Array<{
    id: string;
    name: string;
    description: string;
    icon: string;
  }>;
  completedEpisodesCount: number;
}

async function fetchFinalEnding(sessionId: string): Promise<FinalEndingResponse> {
  const response = await fetch(`/api/game/${sessionId}/ending`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('jwt_token')}`,
      'Content-Type': 'application/json'
    }
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch ending: ${response.statusText}`);
  }

  return await response.json();
}

// 사용
try {
  const ending = await fetchFinalEnding('session_abc123');

  if (ending.finalEnding) {
    console.log('엔딩 제목:', ending.finalEnding.title);
    console.log('엔딩 내용:', ending.finalEnding.summary);
    console.log('엔딩 타입:', ending.finalEnding.type);
  } else {
    console.log('기본 엔딩');
  }

  console.log('최종 게이지:', ending.finalGaugeStates);
  console.log('완료 에피소드:', ending.completedEpisodesCount);
} catch (error) {
  console.error('엔딩 조회 실패:', error);
}
```

### 2. React 컴포넌트

```tsx
import React, { useEffect, useState } from 'react';

interface EndingScreenProps {
  sessionId: string;
}

const EndingScreen: React.FC<EndingScreenProps> = ({ sessionId }) => {
  const [ending, setEnding] = useState<FinalEndingResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadEnding = async () => {
      try {
        const response = await fetch(`/api/game/${sessionId}/ending`, {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('jwt_token')}`
          }
        });

        if (!response.ok) {
          throw new Error('Failed to load ending');
        }

        const data = await response.json();
        setEnding(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    };

    loadEnding();
  }, [sessionId]);

  if (loading) return <div>Loading ending...</div>;
  if (error) return <div>Error: {error}</div>;
  if (!ending) return null;

  const endingType = ending.finalEnding?.type || 'DEFAULT';
  const backgroundColor = {
    HAPPY: '#FFD700',
    BAD: '#2C2C2C',
    NEUTRAL: '#FFFFFF'
  }[endingType] || '#FFFFFF';

  return (
    <div style={{ backgroundColor, padding: '2rem' }}>
      <h1>{ending.finalEnding?.title || 'THE END'}</h1>
      <p>{ending.finalEnding?.summary || '이야기가 끝났습니다.'}</p>

      <h2>최종 게이지</h2>
      <div>
        {ending.gaugeDefinitions?.map(gauge => (
          <div key={gauge.id}>
            {gauge.icon} {gauge.name}: {ending.finalGaugeStates[gauge.id]}
          </div>
        ))}
      </div>

      <p>완료한 에피소드: {ending.completedEpisodesCount}개</p>
    </div>
  );
};

export default EndingScreen;
```

### 3. cURL

```bash
# 최종 엔딩 조회
curl -X GET "http://localhost:8080/api/game/session_abc123/ending" \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json"
```

---

## 기존 API와의 차이점

### GET /api/game/{sessionId} (기존)

- 게임 진행 중에도 호출 가능
- 현재 노드, 선택지 등 게임 상태 전체 반환
- `isGameEnd` 플래그로 게임 종료 여부 확인 필요
- `finalEnding`은 게임 종료 시에만 포함

**사용 시기**: 게임 플레이 중 상태 조회

### GET /api/game/{sessionId}/ending (신규) ⭐

- 게임 완료 후에만 호출 가능
- 최종 엔딩 정보만 간결하게 반환
- 게임이 완료되지 않으면 에러 반환
- 엔딩 화면 표시에 필요한 정보만 포함

**사용 시기**: 엔딩 화면 표시, 엔딩 정보 조회

---

## 프론트엔드 플로우

### 권장 플로우

```typescript
// 1. 게임 플레이 중
const gameState = await fetch(`/api/game/${sessionId}`).then(r => r.json());

if (gameState.isGameEnd) {
  // 2. 게임 종료 감지 → 엔딩 전용 API 호출
  const ending = await fetch(`/api/game/${sessionId}/ending`).then(r => r.json());

  // 3. 엔딩 화면 표시
  showEndingScreen(ending);
} else {
  // 일반 게임 플레이 화면
  showGamePlayScreen(gameState);
}
```

### 대체 플로우 (기존 API만 사용)

```typescript
// 기존 API만 사용해도 엔딩 표시 가능
const gameState = await fetch(`/api/game/${sessionId}`).then(r => r.json());

if (gameState.isGameEnd) {
  // finalEnding 필드 사용
  showEndingScreen({
    title: gameState.finalEnding?.title || gameState.episodeTitle,
    summary: gameState.finalEnding?.summary || gameState.nodeText,
    type: gameState.finalEnding?.type,
    gauges: gameState.gaugeStates
  });
}
```

---

## 테스트

### 단위 테스트

```bash
./gradlew test --tests GameServiceEndingTest
```

### 통합 테스트

```bash
./gradlew test --tests GameServiceEndingIntegrationTest
```

### API 테스트 (Postman/Insomnia)

1. 게임 시작 → `POST /api/game/start`
2. 게임 진행 → `POST /api/game/{sessionId}/choice`
3. 게임 완료까지 반복
4. 최종 엔딩 조회 → `GET /api/game/{sessionId}/ending`

---

## 에러 처리

### 프론트엔드 에러 처리 예시

```typescript
async function fetchEnding(sessionId: string) {
  try {
    const response = await fetch(`/api/game/${sessionId}/ending`, {
      headers: {
        'Authorization': `Bearer ${getToken()}`
      }
    });

    if (response.status === 400) {
      // 게임이 아직 완료되지 않음
      alert('게임을 먼저 완료해주세요.');
      return null;
    }

    if (response.status === 403) {
      // 권한 없음
      alert('이 게임에 접근할 권한이 없습니다.');
      return null;
    }

    if (response.status === 404) {
      // 세션을 찾을 수 없음
      alert('게임 세션을 찾을 수 없습니다.');
      return null;
    }

    if (!response.ok) {
      throw new Error(`API error: ${response.status}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Failed to fetch ending:', error);
    return null;
  }
}
```

---

## 요약

**새로운 API의 장점**:
- ✅ 명확한 용도 (엔딩 조회 전용)
- ✅ 간결한 응답 (필요한 정보만)
- ✅ 명시적 에러 처리 (게임 미완료 시 에러)
- ✅ 프론트엔드 코드 간소화

**기존 API도 계속 사용 가능**:
- `GET /api/game/{sessionId}`도 `finalEnding` 포함
- 두 API 모두 동일한 엔딩 정보 제공
- 프로젝트 상황에 맞게 선택 사용
