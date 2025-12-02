# Top-Down 노드 재생성 가이드

점진적 스토리 편집을 위한 Top-Down 재생성 방식 구현 가이드입니다.

## 개요

사용자가 상위 노드를 수정하면, 그 아래의 모든 하위 노드들을 AI가 자동으로 재생성하는 기능입니다.

### 동작 방식

```
Episode 1 (전체 생성 완료):
  Node 0 (depth 0) ← 사용자가 "어두운 숲" → "밝은 숲"으로 수정
    ├─ Node 1 (depth 1) ← AI가 자동 재생성 (밝은 숲에 맞게)
    │   ├─ Node 3 (depth 2) ← AI가 자동 재생성
    │   └─ Node 4 (depth 2) ← AI가 자동 재생성
    └─ Node 2 (depth 1) ← AI가 자동 재생성
        ├─ Node 5 (depth 2) ← AI가 자동 재생성
        └─ Node 6 (depth 2) ← AI가 자동 재생성
```

---

## 전체 플로우

### 1. 스토리 생성 (기존 방식 유지)

```
POST /api/stories/{id}/generate

→ AI가 전체 에피소드 생성 (모든 depth)
→ S3에 저장
→ Frontend에서 트리 구조로 표시
```

### 2. 노드 수정 및 재생성 (새로운 기능)

```
사용자가 특정 노드 선택 및 수정
  ↓
PUT /api/stories/{storyId}/episodes/{episodeOrder}/nodes/{nodeId}/regenerate

Request:
{
  "nodeText": "수정된 노드 텍스트",
  "choices": ["선택지1", "선택지2"],
  "situation": "수정된 상황",
  "npcEmotions": {"간달프": "기쁨"},
  "tags": ["명랑", "희망"]
}

  ↓
Backend: S3에서 전체 스토리 로드
  ↓
Backend: 해당 노드 찾아서 수정
  ↓
Backend → Relay Server → Analysis AI
  ↓
AI: 수정된 노드 기반으로 하위 노드들 재생성
  ↓
Backend: 재생성된 노드들로 서브트리 교체
  ↓
Backend: S3에 업데이트된 스토리 저장
  ↓
Response:
{
  "status": "success",
  "message": "Subtree regenerated successfully",
  "regeneratedNodes": [...],
  "totalNodesRegenerated": 6
}
```

---

## API 명세

### Backend API

#### PUT /api/stories/{storyId}/episodes/{episodeOrder}/nodes/{nodeId}/regenerate

**설명**: 특정 노드를 수정하고 그 아래 서브트리를 재생성합니다.

**Path Parameters**:
- `storyId` (Long): 스토리 데이터 ID
- `episodeOrder` (Integer): 에피소드 순서 (1, 2, 3, ...)
- `nodeId` (String): 수정할 노드의 ID

**Request Body** (`UpdateNodeRequestDto`):
```json
{
  "nodeText": "주인공은 밝은 숲 속을 걷고 있었다.",
  "choices": [
    "숲 속 깊이 들어간다",
    "숲을 벗어난다"
  ],
  "situation": "밝은 햇살이 나무 사이로 비치는 평화로운 숲",
  "npcEmotions": {
    "요정": "환영",
    "나무정령": "호기심"
  },
  "tags": ["평화", "자연", "희망"]
}
```

**Response** (`RegenerateSubtreeResponseDto`):
```json
{
  "status": "success",
  "message": "Subtree regenerated successfully",
  "regeneratedNodes": [
    {
      "id": "node_1",
      "text": "...",
      "choices": [...],
      "depth": 1,
      "children": [...]
    },
    ...
  ],
  "totalNodesRegenerated": 6
}
```

---

### Relay Server API

#### POST /ai/regenerate-subtree

**설명**: Analysis AI 서버로 서브트리 재생성 요청을 중계합니다.

**Request Body** (`SubtreeRegenerationRequestDto`):
```json
{
  "episodeTitle": "숲 속의 만남",
  "episodeOrder": 1,
  "parentNode": {
    "nodeId": "node_0",
    "text": "주인공은 밝은 숲 속을 걷고 있었다.",
    "choices": ["숲 속 깊이 들어간다", "숲을 벗어난다"],
    "situation": "밝은 햇살이 나무 사이로 비치는 평화로운 숲",
    "npcEmotions": {"요정": "환영"},
    "tags": ["평화", "자연"],
    "depth": 0
  },
  "currentDepth": 0,
  "maxDepth": 3,
  "novelContext": "원작 소설 텍스트...",
  "previousChoices": [],
  "selectedGaugeIds": ["gauge_1", "gauge_2"]
}
```

**Response** (`SubtreeRegenerationResponseDto`):
```json
{
  "status": "success",
  "message": "Subtree regenerated",
  "regeneratedNodes": [...],
  "totalNodesRegenerated": 6
}
```

---

## Python Analysis AI 구현 (예정)

### POST /regenerate-subtree

Analysis AI 서버에서 구현해야 할 엔드포인트입니다.

```python
@app.post("/regenerate-subtree")
async def regenerate_subtree(request: SubtreeRegenerationRequest):
    """
    수정된 노드 아래의 서브트리를 재생성
    """
    parent_node = request.parent_node
    max_depth = request.max_depth
    current_depth = request.current_depth

    # 1. 부모 노드 정보 추출
    parent_text = parent_node.text
    parent_choices = parent_node.choices

    # 2. 각 선택지에 대한 자식 노드 생성
    regenerated_nodes = []

    for choice_idx, choice_text in enumerate(parent_choices):
        # 선택지에 따른 다음 노드 생성
        child_node = generate_child_node(
            parent_text=parent_text,
            choice=choice_text,
            depth=current_depth + 1,
            max_depth=max_depth,
            context=request.novel_context
        )

        # 재귀적으로 하위 노드 생성
        if child_node.depth < max_depth:
            child_node.children = generate_subtree(
                parent_node=child_node,
                max_depth=max_depth,
                context=request.novel_context
            )

        regenerated_nodes.append(child_node)

    return {
        "status": "success",
        "message": "Subtree regenerated",
        "regeneratedNodes": regenerated_nodes,
        "totalNodesRegenerated": count_nodes(regenerated_nodes)
    }

def generate_child_node(parent_text, choice, depth, max_depth, context):
    """단일 자식 노드 생성"""
    # LLM 프롬프트 구성
    prompt = f"""
    부모 노드: {parent_text}
    선택한 선택지: {choice}

    이 선택 이후의 스토리를 생성하세요.
    - 현재 depth: {depth}
    - 최대 depth: {max_depth}

    원작 컨텍스트: {context[:500]}

    JSON 형식으로 반환:
    {{
      "text": "다음 스토리 텍스트",
      "choices": ["선택지1", "선택지2"],
      "situation": "상황 설명",
      "npcEmotions": {{"캐릭터": "감정"}},
      "tags": ["태그1", "태그2"]
    }}
    """

    # LLM 호출
    response = llm.generate(prompt)
    node_data = json.loads(response)

    return StoryNode(
        id=f"node_{uuid.uuid4()}",
        text=node_data["text"],
        choices=node_data["choices"],
        depth=depth,
        details=node_data
    )

def generate_subtree(parent_node, max_depth, context):
    """재귀적으로 서브트리 생성"""
    if parent_node.depth >= max_depth:
        return []

    children = []
    for choice in parent_node.choices:
        child = generate_child_node(
            parent_text=parent_node.text,
            choice=choice,
            depth=parent_node.depth + 1,
            max_depth=max_depth,
            context=context
        )

        if child.depth < max_depth:
            child.children = generate_subtree(child, max_depth, context)

        children.append(child)

    return children
```

---

## 프론트엔드 구현 예시

### React 컴포넌트

```jsx
import React, { useState } from 'react';
import axios from 'axios';

function NodeEditor({ storyId, episodeOrder, node }) {
  const [nodeText, setNodeText] = useState(node.text);
  const [choices, setChoices] = useState(node.choices);
  const [isRegenerating, setIsRegenerating] = useState(false);

  const handleRegenerateSubtree = async () => {
    setIsRegenerating(true);

    try {
      const response = await axios.put(
        `/api/stories/${storyId}/episodes/${episodeOrder}/nodes/${node.id}/regenerate`,
        {
          nodeText,
          choices,
          situation: node.details.situation,
          npcEmotions: node.details.npcEmotions,
          tags: node.details.tags
        }
      );

      console.log(`${response.data.totalNodesRegenerated} nodes regenerated!`);

      // 트리 UI 업데이트
      updateStoryTree(response.data.regeneratedNodes);

      alert('서브트리가 성공적으로 재생성되었습니다!');
    } catch (error) {
      console.error('Regeneration failed:', error);
      alert('재생성 실패: ' + error.message);
    } finally {
      setIsRegenerating(false);
    }
  };

  return (
    <div className="node-editor">
      <h3>노드 편집: {node.id}</h3>

      <div>
        <label>노드 텍스트:</label>
        <textarea
          value={nodeText}
          onChange={(e) => setNodeText(e.target.value)}
          rows={5}
        />
      </div>

      <div>
        <label>선택지:</label>
        {choices.map((choice, idx) => (
          <input
            key={idx}
            value={choice}
            onChange={(e) => {
              const newChoices = [...choices];
              newChoices[idx] = e.target.value;
              setChoices(newChoices);
            }}
          />
        ))}
      </div>

      <button
        onClick={handleRegenerateSubtree}
        disabled={isRegenerating}
      >
        {isRegenerating ? '재생성 중...' : '이 노드 아래 재생성'}
      </button>

      <p className="warning">
        ⚠️ 이 작업은 현재 노드 아래의 모든 하위 노드를 삭제하고 새로 생성합니다.
      </p>
    </div>
  );
}
```

---

## 사용 시나리오

### 시나리오 1: 스토리 분위기 변경

```
1. AI가 "어두운 숲 속을 걷는다" 스토리 생성
   - 하위 노드들도 모두 어두운 분위기

2. 사용자가 루트 노드를 "밝은 숲 속을 걷는다"로 수정

3. API 호출:
   PUT /api/stories/1/episodes/1/nodes/node_0/regenerate

4. AI가 하위 노드들을 밝은 분위기로 재생성
   - "요정을 만난다" (어두운 → 밝은)
   - "아름다운 호수를 발견한다" (새로 추가)
```

### 시나리오 2: 캐릭터 변경

```
1. AI가 "마법사를 만난다" 스토리 생성

2. 사용자가 "전사를 만난다"로 수정

3. 재생성 → 하위 노드들이 전사 관련 이야기로 변경
   - "마법 배우기" → "검술 배우기"
   - "마법서 찾기" → "전설의 검 찾기"
```

---

## 주의사항

### 1. 데이터 손실 경고

재생성 시 기존 하위 노드들이 **완전히 삭제**되고 새로 생성됩니다.
- Frontend에서 확인 모달 표시 권장
- 되돌리기 기능 구현 고려 (버전 관리)

### 2. 성능 고려

서브트리 재생성은 시간이 걸립니다:
- Depth 1: ~5초
- Depth 2: ~15초
- Depth 3: ~30초

Frontend에서 로딩 인디케이터 표시 필수

### 3. AI 제약

- AI가 항상 완벽한 일관성을 보장하지는 않음
- 수정한 노드와 재생성된 노드 간 검토 필요
- 필요 시 재생성 재시도 가능

---

## 다음 단계

1. ✅ Backend & Relay Server 구현 완료
2. ⏳ Analysis AI `/regenerate-subtree` 엔드포인트 구현
3. ⏳ Frontend 노드 편집 UI 구현
4. ⏳ 테스트 및 검증

Analysis AI 개발 완료 후 즉시 사용 가능합니다!
