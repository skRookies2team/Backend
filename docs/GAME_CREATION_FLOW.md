# Game Creation Flow

이 문서는 사용자가 새로운 인터랙티브 스토리를 생성하는 전체 과정을 설명합니다. 이 과정은 사용자의 초기 아이디어를 AI와 협력하여 단계별로 완성된 스토리로 발전시키는 대화형(Interactive) 프로세스입니다.

## 아키텍처 구성 요소

- **User (Creator)**: 스토리를 만드는 사용자. 프론트엔드 UI를 통해 시스템과 상호작용합니다.
- **Backend (Spring Boot)**: 핵심 비즈니스 로직, DB 관리, AI 서버와의 통신을 담당합니다.
- **AI Server (Python)**: 텍스트를 분석하고, 스토리의 에피소드(노드, 선택지, 엔딩)를 생성하는 역할을 합니다.
- **MariaDB**: 생성된 스토리의 구조(에피소드, 노드, 선택지 등)를 영구적으로 저장하는 주 데이터베이스입니다.
- **AWS S3**: 스토리 데이터의 스냅샷 백업 및 미디어 파일을 저장하는 보조 저장소입니다.

## 생성 흐름도 (Step-by-Step)

1.  **초기 설정 (Story Configuration)**
    -   사용자가 프론트엔드에서 스토리의 제목, 장르, 주요 캐릭터 등 기본 정보를 입력합니다.
    -   필요에 따라 초기 아이디어를 제공하기 위한 소설 텍스트 파일 등을 업로드할 수 있습니다.
    -   `POST /api/stories/start` 요청을 통해 백엔드에 스토리 생성 시작을 알립니다.
    -   백엔드는 `StoryCreation` 엔티티를 생성하고, 상태를 `ANALYZING_NOVEL` 등으로 초기화합니다.

2.  **AI 에피소드 생성 (Episode Generation)**
    -   백엔드의 `SequentialGenerationService`가 현재 스토리 상태를 기반으로 AI 서버에 에피소드 생성을 요청합니다. (`POST {AI_SERVER_URL}/generate/episode`)
    -   AI 서버는 요청 내용을 바탕으로 하나의 에피소드에 해당하는 스토리 조각(여러 개의 `StoryNode`와 각 노드에 연결된 `StoryChoice`)을 생성하여 응답합니다.

3.  **DB 저장 및 S3 백업 (Saving & Backup)**
    -   백엔드는 AI로부터 받은 에피소드 데이터를 `Episode`, `StoryNode`, `StoryChoice` 등의 엔티티로 변환합니다.
    -   변환된 엔티티들을 **MariaDB**에 저장하여 스토리 구조를 영구적으로 기록합니다.
    -   백엔드는 DB에 저장된 현재까지의 전체 스토리 구조(`FullStoryDto`)를 JSON 형태로 직렬화하여 **AWS S3**에 스냅샷으로 업로드(백업)합니다.
    -   `StoryCreation` 엔티티의 상태를 `AWAITING_USER_ACTION`으로 변경하고, S3 파일 키를 업데이트합니다.

4.  **사용자 검토 및 수정 (User Review & Edit)**
    -   프론트엔드는 백엔드에 생성된 에피소드 데이터를 요청하여 사용자에게 시각적으로 보여줍니다. (`GET /api/story-edit/stories/{storyId}/episodes/{episodeOrder}`)
    -   사용자는 생성된 노드의 텍스트나 내용을 검토하고, 마음에 들지 않는 부분을 직접 수정할 수 있습니다. (`PUT /api/story-edit/nodes/{nodeId}`)
    -   수정된 내용은 즉시 MariaDB에 반영됩니다.

5.  **하위 트리 재생성 (Subtree Regeneration) - (선택 사항)**
    -   사용자가 특정 노드의 내용을 수정한 후, 그 변경점이 이후 스토리에 큰 영향을 미치길 원할 경우 '하위 트리 재생성'을 요청할 수 있습니다. (`POST /api/story-edit/nodes/{nodeId}/regenerate`)
    -   백엔드는 해당 노드를 포함한 그 하위의 모든 노드와 선택지를 DB에서 삭제합니다.
    -   이후 `Step 2`로 돌아가, 수정된 노드를 기점으로 새로운 하위 스토리를 AI에 요청하여 재생성합니다.

6.  **다음 에피소드 생성 또는 종료 (Next Episode or Finish)**
    -   사용자가 현재 생성된 에피소드에 만족하면, '다음 에피소드 생성'을 요청합니다.
    -   백엔드는 `Step 2`부터의 과정을 반복하여 새로운 에피소드를 생성하고 DB에 추가합니다.
    -   AI가 스토리가 완결되었다고 판단하면, 최종 엔딩(`FinalEnding`)을 생성하고 전체 생성 프로세스는 `COMPLETED` 상태로 종료됩니다.

이처럼 스토리 생성은 'AI 생성 -> DB 저장 -> 사용자 검토/수정 -> 다음 단계 진행'의 사이클을 반복하는 대화형 방식으로 이루어집니다.
