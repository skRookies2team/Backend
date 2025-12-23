# NPC 대화 시스템 - 주요 문제점 및 해결 방안

## 🔴 발견된 주요 문제들

### 1. StoryId vs StoryDataId 불일치 (✅ 해결됨)

**문제**: 프론트엔드는 StoryData ID (숫자)를 사용하지만, 기존 API는 StoryCreation ID (문자열)를 기대

**해결**:
- 새로운 엔드포인트 추가: `GET /api/game/stories/{storyDataId}/selected-characters`
- DTO에 `storyId`와 `storyDataId` 모두 포함

---

### 2. Character ID 매핑 불일치 (✅ 완전 해결됨)

#### 초기 문제 상황

시스템이 스토리별 대화만 지원했고, 프론트엔드가 전달하는 ID와 백엔드가 기대하는 ID가 불일치했습니다.

#### 최종 해결 방안 (✅ 구현 완료)

**캐릭터별 독립 대화 시스템으로 완전히 재구축**

1. **CharacterDto에 chatCharacterId 필드 추가** (src/main/java/com/story/game/common/dto/CharacterDto.java:21)
   ```java
   @Data
   @Builder
   public class CharacterDto {
       private String name;
       private List<String> aliases;
       private String description;
       private List<String> relationships;
       private String chatCharacterId;  // ⭐ 각 캐릭터마다 고유한 ID
   }
   ```

2. **인덱싱 방식 변경** (src/main/java/com/story/game/creation/service/StoryManagementService.java:426-547)
   ```java
   // 각 캐릭터를 개별적으로 인덱싱
   for (CharacterDto character : selectedCharacters) {
       String characterId = storyCreation.getId() + "_" + character.getName();

       CharacterIndexRequestDto indexRequest = CharacterIndexRequestDto.builder()
           .characterId(characterId)  // ⭐ 캐릭터별 고유 ID
           .name(character.getName())
           .description(characterDescription.toString())
           .personality(character.getDescription())
           .background(storyContext.toString())
           .build();

       ragService.indexCharacter(indexRequest);
       log.info("✅ 캐릭터 인덱싱 완료: {} (ID: {})", character.getName(), characterId);
   }
   ```

3. **응답 DTO에 캐릭터별 ID 포함** (src/main/java/com/story/game/gameplay/service/GameService.java:801-824)
   ```java
   List<CharacterDto> selectedCharacters = allCharacters.stream()
       .filter(c -> selectedNames.contains(c.getName()))
       .map(c -> {
           String chatCharId = storyCreation.getId() + "_" + c.getName();
           return CharacterDto.builder()
               .name(c.getName())
               .aliases(c.getAliases())
               .description(c.getDescription())
               .relationships(c.getRelationships())
               .chatCharacterId(chatCharId)  // ⭐ 캐릭터별 고유 ID 할당
               .build();
       })
       .collect(Collectors.toList());
   ```

#### 새로운 시스템 구조

| 단계 | 사용하는 ID | 예시 | 위치 |
|------|------------|------|------|
| **캐릭터 인덱싱** | `{storyId}_{characterName}` | `"story_abc12345_홍길동"` | StoryManagementService:426-547 |
| **RAG 서버 저장** | `{storyId}_{characterName}` | `"story_abc12345_홍길동"` | RagService:118 |
| **API 응답** | `{storyId}_{characterName}` | `"story_abc12345_홍길동"` | CharacterDto.chatCharacterId |
| **프론트엔드 대화 API 호출** | `character.chatCharacterId` | `"story_abc12345_홍길동"` | character-chat.api.ts |

**결과**: ✅ **각 캐릭터가 독립적인 대화 세션을 가짐!**

---

### 3. 설계상 주의사항 ℹ️

#### 현재 구조의 특징

**캐릭터별 대화가 아닌 스토리별 대화**:
- 한 스토리의 모든 캐릭터가 **동일한 characterId를 공유**
- RAG 시스템에 인덱싱된 정보는 스토리 전체의 캐릭터 정보
- 실제로는 "스토리 컨텍스트 대화" 시스템

#### 예시

만약 "홍길동전"에 **홍길동**, **김철수** 두 캐릭터가 있다면:

```json
{
  "chatCharacterId": "story_abc12345",  // 두 캐릭터가 같은 ID 사용!
  "selectedCharacters": [
    { "name": "홍길동" },
    { "name": "김철수" }
  ]
}
```

**프론트엔드 대화 API 호출 시**:
```typescript
// 홍길동과 대화
api.rag.chat({
  characterId: "story_abc12345",  // 스토리 ID 사용
  userMessage: "안녕하세요"
})

// 김철수와 대화 (같은 characterId!)
api.rag.chat({
  characterId: "story_abc12345",  // 동일한 스토리 ID
  userMessage: "안녕하세요"
})
```

#### 영향

- ✅ **장점**: 스토리 전체 컨텍스트를 유지하면서 대화 가능
- ❌ **단점**: 캐릭터별로 독립적인 대화 내역을 관리할 수 없음
- ⚠️ **주의**: 프론트엔드 UI에서 캐릭터 선택은 시각적 요소일 뿐, 백엔드는 구분하지 않음

---

## 📋 프론트엔드 수정 사항 요약

### 1. API 응답 처리 수정

```typescript
// +page.svelte 수정 필요
const selectedCharactersResponse = await api.game.getSelectedCharactersByStoryDataId(storyId);

// ⭐ chatCharacterId 저장 (중요!)
const chatCharacterId = selectedCharactersResponse.chatCharacterId;

// 캐릭터 변환
const characters: Character[] = selectedCharactersResponse.selectedCharacters.map((char) => ({
  id: char.name.toLowerCase().replace(/\s+/g, '-'),
  chatId: chatCharacterId,  // ⭐ 새로 추가: 실제 대화용 ID
  name: char.name,
  ...
}));
```

### 2. 대화 API 호출 수정

```typescript
// character-chat.api.ts (또는 character-chat.svelte)

// ❌ 기존 코드
export async function sendMessage(character: Character, message: string) {
  const response = await api.rag.chat({
    characterId: character.id,  // 잘못된 ID!
    userMessage: message
  });
}

// ✅ 수정 필요
export async function sendMessage(character: Character, message: string) {
  const response = await api.rag.chat({
    characterId: character.chatId,  // 올바른 ID 사용!
    userMessage: message
  });
}
```

### 3. 타입 정의 업데이트

```typescript
// types/game-state.ts (또는 해당 파일)
interface Character {
  id: string;           // UI용 ID (예: "hong-gildong")
  chatId?: string;      // ⭐ 새로 추가: RAG API용 ID (예: "story_abc12345")
  name: string;
  description: string;
  personality: string;
  knowledgeBase: string[];
}

// API 타입 정의
interface SelectedCharactersResponseDto {
  storyId: string;
  storyDataId: number | null;
  chatCharacterId: string;      // ⭐ 새로 추가
  hasSelection: boolean;
  selectedCharacterNames: string[];
  selectedCharacters: CharacterDto[];
}
```

---

## ✅ 최종 체크리스트

### 백엔드 (완료됨)

- [x] `SelectedCharactersResponseDto`에 `chatCharacterId` 필드 추가
- [x] `StoryManagementService`에서 `chatCharacterId` 설정
- [x] `GameService`에서 `chatCharacterId` 설정
- [x] 새로운 엔드포인트 `GET /api/game/stories/{storyDataId}/selected-characters` 추가

### 프론트엔드 (수정 필요)

- [ ] `Character` 타입에 `chatId` 필드 추가
- [ ] 선택된 캐릭터 로드 시 `chatId` 설정
- [ ] 대화 API 호출 시 `chatId` 사용
- [ ] API 응답 타입에 `chatCharacterId` 추가

---

## 🚨 중요 주의사항

### 1. characterId 사용 규칙

| API | characterId 파라미터에 전달할 값 |
|-----|--------------------------------|
| `POST /api/rag/chat` | `chatCharacterId` (= `storyId`) |
| `GET /api/rag/conversations/{characterId}` | `chatCharacterId` (= `storyId`) |
| `DELETE /api/rag/conversations/{characterId}` | `chatCharacterId` (= `storyId`) |

### 2. 대화 내역 관리

- 대화 내역은 **스토리별**로 저장됨 (캐릭터별이 아님)
- 같은 스토리의 모든 캐릭터는 **동일한 대화 컨텍스트**를 공유
- `ChatConversation` 테이블의 `characterId` 컬럼 = StoryCreation ID

### 3. 테스트 시나리오

1. **정상 시나리오**:
   ```
   1. 스토리 생성 (storyId = "story_abc12345" 생성)
   2. 캐릭터 선택 (홍길동, 김철수 선택)
   3. 캐릭터 인덱싱 (characterId = "story_abc12345"로 인덱싱)
   4. 게임 시작
   5. 선택된 캐릭터 조회 (chatCharacterId = "story_abc12345" 획득)
   6. NPC 대화 (characterId = "story_abc12345" 사용)
   ```

2. **에러 시나리오**:
   ```
   ❌ characterId = "홍길동" 사용 → RAG 서버에서 404 Not Found
   ✅ characterId = "story_abc12345" 사용 → 정상 작동
   ```

---

## 📊 시스템 아키텍처 다이어그램

```
[프론트엔드]
    ↓
    1. GET /api/game/stories/1/selected-characters
    ↓
[백엔드 - GameService]
    ↓
    응답: {
      chatCharacterId: "story_abc12345",  ← 이 값을 저장!
      selectedCharacters: [...]
    }
    ↓
[프론트엔드]
    ↓
    2. POST /api/rag/chat
    Body: {
      characterId: "story_abc12345",  ← chatCharacterId 사용!
      userMessage: "안녕하세요"
    }
    ↓
[백엔드 - RagService]
    ↓
    3. POST http://localhost:8081/ai/chat/message
    Body: {
      session_id: "story_abc12345",  ← characterId가 session_id로 변환
      message: "안녕하세요"
    }
    ↓
[Relay Server] → [AI-NPC Server]
```

---

## 🔧 개선 제안 (선택사항)

### 캐릭터별 독립 대화가 필요한 경우

현재 구조를 변경하려면:

1. **인덱싱 방식 변경**:
   ```java
   // 각 캐릭터마다 별도 인덱싱
   for (CharacterDto character : selectedCharacters) {
       String characterId = storyCreation.getId() + "_" + character.getName();
       ragService.indexCharacter(characterId, ...);
   }
   ```

2. **CharacterDto에 chatId 포함**:
   ```java
   // 각 캐릭터에 고유 chatId 할당
   character.setChatId(storyCreation.getId() + "_" + character.getName());
   ```

3. **프론트엔드에서 캐릭터별 ID 사용**:
   ```typescript
   // 캐릭터마다 다른 chatId 사용
   characterId: character.chatId  // "story_abc12345_홍길동"
   ```

---
