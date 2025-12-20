# NPC 대화 기능 프론트엔드 연동 가이드

## 문제 상황

프론트엔드에서 NPC 캐릭터를 조회할 때 **404 에러**가 발생하는 문제가 있었습니다.

### 원인

- **프론트엔드**: `StoryData`의 ID (숫자, 예: 1, 2, 3)를 사용
- **기존 백엔드 API**: `StoryCreation`의 ID (문자열, 예: "story_abc12345")를 기대
- **결과**: ID 불일치로 인한 404 에러 발생

## 해결 방법

**새로운 API 엔드포인트 추가**: StoryData ID로 선택된 캐릭터를 조회할 수 있는 엔드포인트를 추가했습니다.

---

## 프론트엔드 수정 사항

### 1. API 클라이언트 수정 (`src/lib/api/game-api.ts` 또는 유사 파일)

#### 기존 코드
```typescript
// ❌ 이전 방식 - StoryCreation ID 필요
const selectedCharactersResponse = await api.story.getSelectedCharacters(storyId.toString());
```

#### 수정된 코드
```typescript
// ✅ 새로운 방식 - StoryData ID 사용
const selectedCharactersResponse = await api.game.getSelectedCharactersByStoryDataId(storyId);
```

### 2. API 클래스에 메서드 추가

`game-api.ts` (또는 해당 파일)에 다음 메서드를 추가하세요:

```typescript
export class GameApi {
  // ... 기존 메서드들 ...

  /**
   * StoryData ID로 선택된 NPC 캐릭터 조회
   * @param storyDataId StoryData의 ID (숫자)
   */
  async getSelectedCharactersByStoryDataId(
    storyDataId: number
  ): Promise<SelectedCharactersResponseDto> {
    return httpClient.get<SelectedCharactersResponseDto>(
      `/api/game/stories/${storyDataId}/selected-characters`
    );
  }
}
```

### 3. 게임 플레이 페이지 수정 (`+page.svelte` 또는 유사 파일)

#### 변경 전
```typescript
// 131줄 근처 - ❌ 이전 방식
try {
  const selectedCharactersResponse = await api.story.getSelectedCharacters(storyId.toString());
  if (selectedCharactersResponse.hasSelection && selectedCharactersResponse.selectedCharacters.length > 0) {
    // ...
  }
} catch (err) {
  console.error('Failed to load selected characters:', err);
}
```

#### 변경 후
```typescript
// ✅ 새로운 방식
try {
  const selectedCharactersResponse = await api.game.getSelectedCharactersByStoryDataId(storyId);
  if (selectedCharactersResponse.hasSelection && selectedCharactersResponse.selectedCharacters.length > 0) {
    // ...
  }
} catch (err) {
  console.error('Failed to load selected characters:', err);
}
```

---

## API 상세 정보

### 새로운 엔드포인트

```
GET /api/game/stories/{storyDataId}/selected-characters
```

#### 요청 파라미터

| 이름 | 타입 | 설명 | 예시 |
|------|------|------|------|
| `storyDataId` | `number` | StoryData의 ID | `1`, `2`, `3` |

#### 응답 형식

```typescript
interface SelectedCharactersResponseDto {
  storyId: string;                        // StoryCreation ID (예: "story_abc12345")
  storyDataId: number | null;             // StoryData ID (예: 1, 2, 3) - 생성 완료 전 null 가능
  hasSelection: boolean;                  // 캐릭터 선택 여부
  selectedCharacterNames: string[];       // 선택된 캐릭터 이름 목록
  selectedCharacters: CharacterDto[];     // 선택된 캐릭터 상세 정보
}

interface CharacterDto {
  name: string;                           // 캐릭터 이름
  description: string;                    // 캐릭터 설명
  aliases?: string[];                     // 별칭 (선택)
  relationships?: string[];               // 관계 정보 (선택)
}
```

#### 응답 예시

**캐릭터가 선택된 경우:**
```json
{
  "storyId": "story_abc12345",
  "storyDataId": 1,
  "hasSelection": true,
  "selectedCharacterNames": ["홍길동", "김철수"],
  "selectedCharacters": [
    {
      "name": "홍길동",
      "description": "의협심이 강한 주인공",
      "aliases": ["길동이", "홍 대협"],
      "relationships": ["김철수의 친구"]
    },
    {
      "name": "김철수",
      "description": "홍길동의 든든한 조력자",
      "aliases": [],
      "relationships": ["홍길동의 친구"]
    }
  ]
}
```

**캐릭터가 선택되지 않은 경우:**
```json
{
  "storyId": "story_abc12345",
  "storyDataId": 1,
  "hasSelection": false,
  "selectedCharacterNames": [],
  "selectedCharacters": []
}
```

---

## 테스트 방법

### 1. 백엔드 테스트

```bash
# StoryData ID = 1로 테스트 (실제 ID로 변경하세요)
curl -X GET "http://localhost:8080/api/game/stories/1/selected-characters" \
  -H "Content-Type: application/json"
```

### 2. 프론트엔드 통합 테스트

1. 소설 업로드 및 스토리 생성
2. 캐릭터 선택 (`POST /api/stories/{storyId}/select-characters`)
3. 게임 시작
4. **새로운 API 호출**: `GET /api/game/stories/{storyDataId}/selected-characters`
5. NPC 대화 UI에 캐릭터 표시 확인

---

## 주의사항

### ⚠️ 중요

1. **StoryData ID vs StoryCreation ID 구분**
   - `StoryData ID`: 숫자 (예: 1, 2, 3) - 게임 플레이용
   - `StoryCreation ID`: 문자열 (예: "story_abc12345") - 스토리 생성용

2. **API 경로 차이**
   - 스토리 생성 API: `/api/stories/{storyId}/...` (StoryCreation ID 사용)
   - 게임 플레이 API: `/api/game/stories/{storyDataId}/...` (StoryData ID 사용)

3. **캐릭터 선택 필수**
   - 게임을 시작하기 전에 반드시 캐릭터를 선택해야 합니다
   - 캐릭터 선택: `POST /api/stories/{storyId}/select-characters`
   - 선택하지 않으면 `hasSelection: false`가 반환됩니다

---

## 문제 해결

### Q1: 여전히 404 에러가 발생합니다

**A**: 다음을 확인하세요:
1. 백엔드 서버가 최신 코드로 재시작되었는지 확인
2. API 경로가 정확한지 확인 (`/api/game/stories/{storyDataId}/selected-characters`)
3. `storyDataId`가 숫자 타입인지 확인

### Q2: `hasSelection: false`가 반환됩니다

**A**: 캐릭터를 아직 선택하지 않은 상태입니다.
- 스토리 생성 과정에서 `POST /api/stories/{storyId}/select-characters` API를 호출했는지 확인
- 해당 API가 성공적으로 완료되었는지 (200 OK) 확인

### Q3: StoryCreation ID를 어떻게 확인하나요?

**A**: 프론트엔드에서는 StoryCreation ID를 직접 사용할 필요가 없습니다.
- 스토리 생성 시: StoryCreation ID 사용 (`story_abc12345`)
- 게임 플레이 시: StoryData ID 사용 (숫자 ID)
- 백엔드가 자동으로 매핑해줍니다

---

## 기타 정보

### 관련 파일
- **백엔드**:
  - `GameController.java`: 새로운 엔드포인트 정의
  - `GameService.java`: 비즈니스 로직 구현
- **프론트엔드**:
  - `game-api.ts`: API 클라이언트
  - `+page.svelte`: 게임 플레이 페이지

### 참고 문서
- [스토리 생성 API 문서](STORY_GENERATION_API.md)
- [게임 플레이 API 문서](GAMEPLAY_API.md) (별도 작성 필요)

---

## 변경 이력

- **2025-12-20 (v2)**: 응답에 스토리 식별자 추가
  - `SelectedCharactersResponseDto`에 `storyId`와 `storyDataId` 필드 추가
  - 프론트엔드에서 어떤 스토리의 캐릭터인지 명확하게 식별 가능

- **2025-12-20 (v1)**: NPC 대화 기능을 위한 새로운 API 엔드포인트 추가
  - `GET /api/game/stories/{storyDataId}/selected-characters` 엔드포인트 추가
  - StoryData ID로 선택된 캐릭터 조회 기능 구현
