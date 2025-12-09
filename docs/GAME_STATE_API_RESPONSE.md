# 게임 상태 API 응답 가이드

## 문제: 선택지만 나오고 본문/도입부가 표시되지 않음

프론트엔드에서 게임 본문(`nodeText`)과 에피소드 도입부(`introText`)가 표시되지 않는 경우, 다음을 확인하세요.

---

## API 응답 구조

### GET /api/game/{sessionId}

#### 정상 응답 예시 (첫 번째 노드 - 도입부 포함)

```json
{
  "sessionId": "session_abc123",
  "currentEpisodeId": "episode-uuid-1",
  "currentNodeId": "node-uuid-0",
  "gaugeStates": {
    "trust": 50,
    "courage": 50
  },
  "accumulatedTags": {},

  // ⭐ 에피소드 정보
  "episodeTitle": "제1화: 운명의 시작",
  "introText": "어둠이 세상을 뒤덮은 지 100년이 지났다. 당신은 작은 마을에서 평범하게 살아왔지만, 오늘 모든 것이 바뀌었다...",

  // ⭐ 현재 노드 본문
  "nodeText": "마을 광장에 사람들이 모여 있다. 마을 장로가 심각한 표정으로 말한다. \"어둠의 군대가 이곳으로 향하고 있소. 우리는 선택해야 합니다.\"",

  "nodeDetails": {
    "situation": "긴장된 마을 회의",
    "npcEmotions": {
      "장로": "걱정스러움",
      "주민들": "두려움"
    }
  },

  // ⭐ 선택지 목록
  "choices": [
    {
      "id": "choice-uuid-1",
      "choiceOrder": 0,
      "text": "마을을 지키기 위해 싸우자",
      "tags": ["brave", "protective"]
    },
    {
      "id": "choice-uuid-2",
      "choiceOrder": 1,
      "text": "마을을 버리고 피난하자",
      "tags": ["cautious", "survival"]
    }
  ],

  "imageUrl": "https://...",
  "gaugeDefinitions": [...],
  "isEpisodeEnd": false,
  "isGameEnd": false
}
```

#### 일반 노드 응답 (도입부 없음)

```json
{
  "sessionId": "session_abc123",
  "episodeTitle": "제1화: 운명의 시작",
  "introText": null,  // ⭐ 첫 노드가 아니면 null
  "nodeText": "당신은 검을 들고 마을 성벽으로 향한다. 멀리서 어둠의 군대가 다가오는 것이 보인다.",
  "choices": [...]
}
```

---

## 프론트엔드 체크리스트

### 1. API 응답 확인

브라우저 개발자 도구(F12) → Network 탭에서 API 응답 확인:

```javascript
// GET /api/game/{sessionId} 응답 확인
{
  "introText": "...",  // ✅ 첫 노드에서는 값이 있어야 함
  "nodeText": "...",   // ✅ 모든 노드에서 값이 있어야 함
  "choices": [...]     // ✅ 선택지 배열
}
```

### 2. 프론트엔드 코드 확인

#### ❌ 잘못된 예시 (필드를 사용하지 않음)

```jsx
function GameScreen({ gameState }) {
  return (
    <div>
      <h1>{gameState.episodeTitle}</h1>
      {/* nodeText와 introText를 표시하지 않음! */}

      <div>
        {gameState.choices.map(choice => (
          <button key={choice.id}>{choice.text}</button>
        ))}
      </div>
    </div>
  );
}
```

#### ✅ 올바른 예시 (필드를 모두 표시)

```jsx
function GameScreen({ gameState }) {
  return (
    <div>
      <h1>{gameState.episodeTitle}</h1>

      {/* ⭐ 도입부 표시 (첫 노드에만 있음) */}
      {gameState.introText && (
        <div className="intro-text">
          <p>{gameState.introText}</p>
        </div>
      )}

      {/* ⭐ 현재 노드 본문 표시 (필수) */}
      <div className="node-text">
        <p>{gameState.nodeText}</p>
      </div>

      {/* 상황 설명 (선택사항) */}
      {gameState.nodeDetails?.situation && (
        <div className="situation">
          <em>{gameState.nodeDetails.situation}</em>
        </div>
      )}

      {/* 선택지 */}
      <div className="choices">
        {gameState.choices.map(choice => (
          <button key={choice.id} onClick={() => selectChoice(choice)}>
            {choice.text}
          </button>
        ))}
      </div>
    </div>
  );
}
```

### 3. CSS 확인

텍스트가 숨겨져 있지 않은지 확인:

```css
/* ❌ 숨겨진 텍스트 */
.node-text {
  display: none; /* 이러면 안 보임! */
}

/* ✅ 보이는 텍스트 */
.node-text {
  display: block;
  margin: 1rem 0;
  line-height: 1.6;
}

.intro-text {
  background-color: #f5f5f5;
  padding: 1rem;
  border-left: 4px solid #4CAF50;
  margin-bottom: 1rem;
}
```

---

## 백엔드 로그 확인

백엔드에 디버깅 로그가 추가되었습니다. 서버 로그에서 다음 내용 확인:

```
=== Building Game State Response ===
Episode: 제1화: 운명의 시작 (order: 1)
Node ID: abc-123, Depth: 0
Show Intro: true
Intro Text: 어둠이 세상을 뒤덮은 지 100년이 지났다...
Node Text: 마을 광장에 사람들이 모여 있다...
Choices Count: 2
====================================
```

**확인 사항**:
- `Intro Text`가 "null"이면 → DB에 intro_text가 저장되지 않음
- `Node Text`가 "null"이면 → DB에 노드 text가 저장되지 않음
- `Show Intro`가 false이면 → 첫 노드가 아니므로 intro는 null이 정상

---

## DB 데이터 확인

### 1. Episode 테이블 확인

```sql
SELECT id, title, episode_order,
       SUBSTRING(intro_text, 1, 100) as intro_preview
FROM episodes
WHERE story_creation_id = 'story_xxx'
ORDER BY episode_order;
```

**확인 사항**:
- `intro_text`가 NULL이면 → AI 생성 시 intro가 생성되지 않음

### 2. StoryNode 테이블 확인

```sql
SELECT id, depth,
       SUBSTRING(text, 1, 100) as text_preview,
       node_type
FROM story_nodes
WHERE episode_id = 'episode_uuid'
ORDER BY depth;
```

**확인 사항**:
- `text`가 NULL이면 → 노드 본문이 저장되지 않음

---

## 문제별 해결 방법

### 문제 1: introText가 표시되지 않음

**원인**:
- 프론트엔드가 `introText` 필드를 사용하지 않음
- CSS로 숨겨져 있음
- 첫 번째 노드가 아니어서 `introText`가 `null` (정상)

**해결**:
```jsx
{gameState.introText && (
  <div className="intro">{gameState.introText}</div>
)}
```

### 문제 2: nodeText가 표시되지 않음

**원인**:
- 프론트엔드가 `nodeText` 필드를 사용하지 않음
- CSS로 숨겨져 있음

**해결**:
```jsx
<div className="story-text">
  {gameState.nodeText}
</div>
```

### 문제 3: DB에 데이터가 없음

**원인**:
- AI 서버가 intro_text나 node text를 생성하지 않음
- DB 저장 로직 오류

**해결**:
1. AI 서버 응답 확인
2. StoryMapper의 저장 로직 확인
3. 수동으로 DB 데이터 업데이트 (임시)

```sql
UPDATE episodes
SET intro_text = '에피소드 도입부 텍스트...'
WHERE id = 'episode_uuid';

UPDATE story_nodes
SET text = '노드 본문 텍스트...'
WHERE id = 'node_uuid';
```

---

## 응답 필드 요약

| 필드 | 타입 | 필수 | 설명 | 표시 조건 |
|------|------|------|------|-----------|
| `episodeTitle` | String | ✅ | 에피소드 제목 | 항상 표시 |
| `introText` | String | ❌ | 에피소드 도입부 | 첫 노드에서만 표시 |
| `nodeText` | String | ✅ | 현재 노드 본문 | 항상 표시 |
| `nodeDetails` | Object | ❌ | 상황/감정 정보 | 선택적 표시 |
| `choices` | Array | ✅ | 선택지 목록 | 항상 표시 |

---

## 테스트 방법

### 1. 백엔드 빌드 및 실행

```bash
./gradlew build
./gradlew bootRun
```

### 2. API 직접 호출

```bash
# 게임 시작
curl -X POST http://localhost:8080/api/game/start \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"storyDataId": 1}'

# 응답에서 sessionId 확인 → "session_abc123"

# 게임 상태 조회
curl -X GET http://localhost:8080/api/game/session_abc123 \
  -H "Authorization: Bearer {token}"
```

### 3. 응답 확인

응답 JSON에서 다음 필드 확인:
```json
{
  "introText": "...",  // ✅ 값이 있는지 확인
  "nodeText": "...",   // ✅ 값이 있는지 확인
  "choices": [...]     // ✅ 배열이 있는지 확인
}
```

---

## 요약

**프론트엔드 개발자가 해야 할 일**:

1. ✅ API 응답에 `introText`, `nodeText` 포함 확인
2. ✅ 프론트엔드 코드에서 해당 필드 사용
3. ✅ CSS로 텍스트가 숨겨지지 않았는지 확인

**백엔드 확인 사항**:

1. ✅ 로그에서 "Intro Text", "Node Text" 값 확인
2. ✅ DB에 실제 데이터 저장 여부 확인
3. ✅ API 응답 JSON에 필드 포함 확인
