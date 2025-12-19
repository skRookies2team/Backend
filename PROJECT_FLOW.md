# 프로젝트 주요 플로우 문서

이 문서는 `story-backend` 프로젝트의 핵심적인 데이터 흐름과 상호작용을 설명합니다. 주요 플로우는 **스토리 생성**, **게임 플레이**, **스토리 편집 및 재생성** 세 가지로 나뉩니다.

## 1. 스토리 생성 플로우 (Episode-by-Episode)

스토리 생성은 `SequentialGenerationService`에 의해 주도되며, AI 서버와 통신하여 한 번에 하나의 에피소드씩 순차적으로 진행됩니다.

1.  **생성 시작 (`startEpisodeGeneration`)**:
    *   사용자가 설정(장르, 인물 등)을 완료하고 생성을 시작하면, `StoryCreation` 엔티티의 상태가 `CONFIGURED`에서 `GENERATING`으로 변경됩니다.
    *   첫 번째 에피소드(Order 1) 생성을 위해 내부적으로 `runEpisodeGenerationTaskSync`가 호출됩니다.

2.  **AI 서버에 에피소드 생성 요청 (`runEpisodeGenerationTaskSync`)**:
    *   **요청 데이터 준비**: AI가 다음 에피소드를 생성하는 데 필요한 모든 컨텍스트를 담아 `GenerateNextEpisodeRequest` 객체를 만듭니다.
        *   `initialAnalysis`: 원작 소설 요약, 등장인물 정보.
        *   `storyConfig`: 총 에피소드 수, 최대 깊이, 게이지 설정.
        *   `novelContext`: 원작 소설 전체 텍스트.
        *   `currentEpisodeOrder`: 생성할 에피소드 번호.
        *   `previousEpisode`: **(핵심)** 바로 이전 에피소드의 전체 데이터. (첫 에피소드 생성 시에는 `null`). 이를 통해 AI가 스토리의 연속성을 유지합니다.
    *   **AI 서버 호출**: 준비된 요청 객체를 `Relay Server`의 `/ai/generate-next-episode` 엔드포인트로 POST 요청을 보냅니다. 백엔드는 AI의 응답(`EpisodeDto`)이 올 때까지 동기적으로 대기(`block()`)합니다.

3.  **결과 처리 및 저장**:
    *   **DB 저장**: AI로부터 받은 `EpisodeDto`를 `StoryMapper`를 통해 `Episode`, `StoryNode`, `StoryChoice`, `EpisodeEnding` 등 JPA 엔티티로 변환하여 MariaDB에 저장합니다.
    *   **S3 스냅샷 백업**: DB 저장이 완료되면, 현재까지 생성된 전체 스토리(`FullStoryDto`)를 JSON 파일로 변환하여 S3에 업로드합니다. 이는 데이터 백업 및 복구 목적으로 사용됩니다.
    *   **상태 업데이트**:
        *   마지막 에피소드인 경우: `StoryCreation`의 상태를 `COMPLETED`로 변경하고, 게임 플레이에서 사용될 `StoryData` 엔티티를 최종적으로 생성합니다.
        *   중간 에피소드인 경우: 상태를 **`AWAITING_USER_ACTION`**으로 변경합니다. 이는 백엔드가 다음 에피소드 생성을 위한 사용자(프론트엔드)의 명시적인 요청을 기다리고 있음을 의미합니다.

4.  **다음 에피소드 생성 (`generateNextEpisode`)**:
    *   사용자가 다음 에피소드 생성을 요청하면 이 메소드가 호출됩니다.
    *   현재까지 완료된 에피소드 수를 확인하고, 다음 에피소드 번호와 직전 에피소드 정보를 담아 다시 `runEpisodeGenerationTaskSync`를 호출하여 2-3번 과정을 반복합니다.

## 2. 게임 플레이 플로우

게임 플레이는 `GameService`가 관리하며, 사용자의 선택에 따라 스토리를 진행시키는 역할을 합니다.

1.  **게임 시작 (`startGame`)**:
    *   사용자가 플레이할 `StoryData`를 선택하면, 해당 스토리에 대한 `GameSession` 엔티티를 새로 생성합니다.
    *   `GameSession`에는 현재 위치(노드 ID), 게이지 초기값(보통 50), 방문 기록 등이 초기화됩니다.
    *   **첫 노드 이미지 생성**: `Relay Server`의 `/ai/generate-image` 엔드포인트에 현재 노드 정보를 보내 첫 장면에 표시될 이미지를 생성하고 URL을 받아옵니다.
    *   첫 번째 노드의 텍스트, 선택지, 이미지 URL 등을 담은 `GameStateResponseDto`를 프론트엔드로 반환합니다.

2.  **선택지 제출 (`makeChoice`)**:
    *   사용자가 선택지를 고르면, 백엔드는 `GameSession`을 조회하여 현재 상태를 파악합니다.
    *   **상태 전이**: 사용자가 선택한 `StoryChoice`에 연결된 다음 `StoryNode`로 `currentNodeId`를 업데이트합니다.
    *   **태그 누적**: 선택지에 부여된 `tags`가 있다면, 이를 `GameSession`의 `accumulatedTags` 맵에 누적합니다. 이 태그들은 에피소드 엔딩 분기 판정에 사용됩니다.
    *   **RAG AI 연동**: 플레이어의 선택과 그로 인한 상황 변화를 텍스트로 구성하여 `ragService.updateGameProgress`를 통해 RAG 기반 NPC AI에게 전달합니다. (게임 진행에 치명적이지 않은 부가 기능)
    *   다음 노드에 대한 이미지 생성을 다시 `/ai/generate-image`에 요청합니다.
    *   업데이트된 게임 상태(`GameStateResponseDto`)를 반환합니다.

3.  **에피소드/게임 종료 처리**:
    *   **에피소드 종료 (`handleEpisodeEnd`)**: 플레이어가 선택지를 더 이상 고를 수 없는 노드(엔딩 노드)에 도달하면 호출됩니다.
        *   **엔딩 판정**: `accumulatedTags`와 `EpisodeEnding`에 설정된 조건(SpEL 표현식)을 비교하여 이번 에피소드의 엔딩을 결정합니다.
        *   **게이지 변경**: 결정된 엔딩에 `gaugeChanges`가 설정되어 있다면, `GameSession`의 `gaugeStates`를 업데이트합니다.
        *   **다음 에피소드로**: 다음 에피소드가 존재하면, 해당 에피소드의 첫 노드로 `GameSession`의 상태를 업데이트하고 `accumulatedTags`를 초기화합니다.
    *   **게임 최종 종료 (`handleGameEnd`)**: 마지막 에피소드가 끝나면 호출됩니다.
        *   **최종 엔딩 판정**: `gaugeStates`와 `StoryCreation`에 설정된 `FinalEnding` 조건을 비교하여 게임의 최종 결말을 결정합니다.
        *   `GameSession`의 `isCompleted` 플래그를 `true`로 설정하고 최종 엔딩 정보를 담아 반환합니다.

## 3. 대화형 스토리 편집 및 재생성 플로우

사용자는 AI가 생성한 에피소드의 내용을 수정하고, 그 수정사항을 기점으로 하위 스토리를 다시 생성할 수 있습니다. 이 기능은 `StoryEditingService`가 담당합니다.

1.  **재생성 요청**:
    *   프론트엔드에서 사용자가 특정 노드의 텍스트를 수정한 뒤 '하위 트리 재생성'을 요청합니다.
    *   `PUT /api/stories/{storyId}/episodes/{episodeOrder}/nodes/{nodeId}/regenerate` 형태의 API가 호출됩니다.

2.  **하위 트리 재생성 (`regenerateSubtreeSync`)**:
    *   **노드 텍스트 업데이트**: 요청받은 `nodeId`의 `StoryNode` 엔티티를 찾아 텍스트를 새로운 내용으로 먼저 업데이트합니다.
    *   **기존 하위 트리 삭제**: JPA의 `orphanRemoval` 기능을 이용해 해당 노드의 모든 하위 선택지(`outgoingChoices`)를 제거합니다. 이로 인해 기존의 모든 자식 노드와 그 하위 트리가 DB에서 연쇄적으로 삭제됩니다.
    *   **AI 서버에 재생성 요청**:
        *   **요청 데이터 준비**: `SubtreeRegenerationRequestDto` 객체에 **새롭게 업데이트된 부모 노드 정보**와 전체 스토리 컨텍스트(원작, 인물 등)를 담습니다.
        *   **AI 서버 호출**: `Relay Server`의 `/ai/regenerate-subtree` 엔드포인트로 POST 요청을 보냅니다. AI는 이 부모 노드로부터 파생되는 새로운 하위 트리(노드 리스트)를 생성하여 반환합니다.
    *   **새 하위 트리 부착**: AI로부터 받은 새로운 노드 DTO 리스트를 `StoryMapper`를 통해 엔티티로 변환하고, 수정된 부모 노드에 새로운 자식으로 연결합니다.
    *   **저장 및 백업**: 변경된 노드 정보를 DB에 저장하고, S3의 전체 스토리 스냅샷도 새로운 내용으로 업데이트합니다.
    *   재생성된 노드 정보를 프론트엔드로 반환하여 화면을 갱신합니다.

## 4. AI 서버 연동 요약

백엔드는 `Relay Server`를 통해 여러 AI 모델과 상호작용하며, 각 기능별로 다른 엔드포인트를 사용합니다.

*   `POST /ai/generate-next-episode`: 이전 에피소드 정보를 바탕으로 다음 에피소드 전체를 생성합니다.
*   `POST /ai/regenerate-subtree`: 수정된 특정 노드를 부모로 삼아 그 아래의 하위 스토리 가지만을 다시 생성합니다.
*   `POST /ai/generate-image`: 게임 플레이 중 현재 노드의 상황에 맞는 이미지를 생성합니다.
*   `RAG Service (내부 통신)`: 플레이어의 선택을 RAG 기반 NPC AI에게 전달하여 대화 컨텍스트를 유지시킵니다.
*   `GET /ai/health`: AI 서버의 상태를 확인합니다.
