# GEMINI 프로젝트 분석: story-backend

이 문서는 `story-backend` 프로젝트에 대한 포괄적인 개요를 제공하며, 향후 개발 및 상호작용을 안내하기 위해 작성되었습니다.

## 프로젝트 개요

이 프로젝트는 인터랙티브 스토리 게임의 백엔드 서버입니다. Java 17 기반의 Spring Boot 애플리케이션으로, 게임 로직, 사용자 인증, 커뮤니티 기능(게시글, 댓글 등) 및 별도의 Python 기반 AI 서버와 통합된 스토리 생성 파이프라인을 관리합니다.

주요 기능은 다음과 같이 변경되었습니다:
- JWT와 Spring Security를 이용한 사용자 인증 및 관리.
- 텍스트를 분석하고 분기형 스토리 콘텐츠를 **대화형(interactive)으로 생성**하기 위해 Python AI 서비스에 요청을 보내는 다단계 스토리 생성 프로세스. 각 에피소드 생성 후 사용자의 검토 및 수정이 가능합니다.
- 생성된 스토리를 통해 플레이어 세션, 선택, 진행 상황을 관리하는 게임 플레이 엔진.
- 사용자가 게시물을 올리고, 댓글을 달고, 스토리를 리뷰할 수 있는 커뮤니티 기능.
- 스토리 구조 데이터는 **MariaDB 데이터베이스에 구조화되어 저장**되며, 사용자 업로드 미디어 및 스토리 진행 상황의 **스냅샷 백업을 위해 AWS S3**와 통합됩니다.

### 핵심 기술
- **프레임워크:** Spring Boot 3.2.0
- **언어:** Java 17
- **빌드 도구:** Gradle
- **데이터베이스:** MariaDB with Spring Data JPA/Hibernate
- **인증:** Spring Security, JSON Web Tokens (JWT)
- **API 문서:** Swagger/OpenAPI
- **파일 저장소:** AWS S3 (스토리 데이터의 스냅샷 백업 및 미디어 파일 저장용)
- **비동기 통신:** AI 서버 호출을 위한 Spring WebFlux `WebClient`
- **환경 설정:** `.env` 파일을 통한 설정 관리

## 빌드 및 실행

### 1. 환경 설정

이 애플리케이션은 MariaDB 데이터베이스와 AI 서버 및 S3 버킷 연결 정보가 필요합니다. 설정은 프로젝트 루트의 `.env` 파일에서 로드됩니다.

**A. Docker Compose 사용 (로컬 설정 권장):**

제공된 `docker-compose.yml`은 MariaDB 컨테이너와 AWS S3를 시뮬레이션하는 LocalStack 컨테이너를 시작합니다.

1.  **컨테이너 시작:**
    ```bash
    docker-compose up -d
    ```

2.  **`.env` 파일 생성:** 루트 디렉터리에 Docker 환경에 맞는 다음 내용으로 `.env` 파일을 생성합니다.
    ```env
    # 데이터베이스 (docker-compose 기반)
    DB_HOST=localhost
    DB_PORT=3306
    DB_NAME=story_game
    DB_USERNAME=root
    DB_PASSWORD=1031

    # AI 서버 (AI 서버 주소로 업데이트)
    AI_SERVER_URL=http://localhost:8000
    AI_SERVER_TIMEOUT=600000

    # LocalStack S3 (docker-compose 기반)
    AWS_S3_BUCKET=story-game-bucket
    AWS_S3_REGION=ap-northeast-2
    AWS_ACCESS_KEY=your-access-key  # LocalStack은 더미 값 사용 가능
    AWS_SECRET_KEY=your-secret-key # LocalStack은 더미 값 사용 가능

    # JWT (운영 환경에서는 강력하고 긴 비밀 키 생성)
    JWT_SECRET=your-very-long-secret-key-at-least-256-bits-long
    JWT_ACCESS_TOKEN_VALIDITY=3600000
    JWT_REFRESH_TOKEN_VALIDITY=604800000

    # 서버 및 앱 설정
    SERVER_PORT=8080
    CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
    JPA_DDL_AUTO=update
    JPA_SHOW_SQL=true
    LOG_LEVEL=INFO
    ```
    *참고: LocalStack 시작 후 S3 버킷을 수동으로 생성해야 할 수 있습니다.*

**B. 수동 설정:**

Docker를 사용하지 않는 경우, MariaDB가 설치되어 실행 중인지 확인하세요. `story_game` 데이터베이스를 생성하고 `.env` 파일의 `DB_*` 변수를 그에 맞게 업데이트하세요.

### 2. 애플리케이션 실행

Gradle 래퍼를 사용하여 프로젝트를 빌드하고 실행합니다.

- **프로젝트 빌드:**
  ```bash
  ./gradlew build
  ```

- **애플리케이션 실행:**
  ```bash
  ./gradlew bootRun
  ```

- **테스트 실행:**
  ```bash
  ./gradlew test
  ```

서버는 `.env`에 지정된 포트(기본값: `8080`)에서 시작됩니다.

## 아키텍처 및 개발

### API 문서

API는 Swagger/OpenAPI를 사용하여 문서화되었습니다. 서버가 실행되면 다음 주소에서 인터랙티브 API 문서에 접근할 수 있습니다:
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### 프로젝트 구조

프로젝트는 기능 도메인별로 구성된 표준 계층형 아키텍처를 따릅니다. `src/main/java/com/story/game/`의 주요 패키지는 다음과 같습니다:
- `auth`: 사용자 인증 및 보안 처리.
- `creation`: 다단계 스토리 생성 파이프라인 관리 (주로 `SequentialGenerationService`를 통해 AI와 상호작용).
- `gameplay`: 인터랙티브 스토리 플레이를 위한 핵심 로직 포함.
- `community`: 게시물, 댓글, 리뷰와 같은 소셜 기능 구현.
- `infrastructure`: S3, WebClient와 같은 외부 서비스 설정 포함.
- `common`: 공유 엔티티, DTO, 예외 핸들러 포함.
- `story`: 새로 추가된 패키지로, 스토리 구조(에피소드, 노드, 선택지, 엔딩) 엔티티 및 관련 리포지토리, 매퍼, 그리고 대화형 스토리 편집 서비스/컨트롤러를 포함합니다.

자세한 내용은 `docs/ARCHITECTURE.md`를 참조하세요.

### 보안 모델

- **인증:** 애플리케이션은 상태 비저장(stateless)이며, 엔드포인트 보안을 위해 JWT를 사용합니다. `JwtAuthenticationFilter`가 요청을 가로채 토큰을 검증합니다.
- **공개 엔드포인트:**
  - 사용자 등록 및 로그인 (`/api/auth/**`).
  - 커뮤니티 콘텐츠 읽기 전용 접근 (`GET /api/posts/**`, `GET /api/reviews/**` 등).
  - 스토리 생성 및 목록 조회 (`/api/stories/**`, `GET /api/game/stories/**`).
  - 파일 업로드 및 상태 확인.
  - Swagger API 문서.
- **보호된 엔드포인트:** 데이터를 수정하는 모든 다른 엔드포인트(예: 게임 선택, 게시물 작성, 댓글 좋아요 등)는 `Authorization: Bearer <token>` 헤더에 유효한 JWT가 필요합니다.

### 개발 가이드라인

- **설정:** 모든 설정 가능한 파라미터(데이터베이스 자격 증명, API 키, URL 등)는 `.env` 파일을 통해 관리됩니다.
- **데이터베이스:** 개발 환경에서는 스키마를 자동으로 조정하는 `JPA_DDL_AUTO=update`로 설정됩니다. 운영 환경에서는 `validate`로 변경해야 합니다.
- **테스팅:** 프로젝트에는 단위 및 통합 테스트가 포함되어 있습니다. `DEVELOPMENT_GUIDE.md`는 `curl`이나 Postman과 같은 도구를 사용한 API 테스트 방법을 상세히 안내합니다.
- **코드 스타일:** 프로젝트는 보일러플레이트 코드를 줄이기 위해 Lombok을 광범위하게 사용합니다. 코딩 스타일 가이드라인은 `docs/CODING_CONVENTIONS.md`를 참조하세요.

## 아키텍처 변경 상세: 대화형 스토리 생성 및 편집

기존 S3 파일 기반의 일괄 스토리 생성 방식에서 **MariaDB 기반의 대화형(Interactive) 생성 및 편집 방식**으로 전환되었습니다. 주요 변경 사항은 다음과 같습니다.

### 1. 데이터 저장 방식의 변화
- **MariaDB (주요 저장소)**:
    - `Episode`, `StoryNode`, `StoryChoice`, `EpisodeEnding`과 같은 스토리의 핵심 구조가 이제 관계형 데이터베이스에 엔티티로 저장됩니다.
    - 각 노드와 선택지는 개별적인 레코드로 관리되어, 특정 부분의 수정 및 조회가 효율적으로 이루어집니다.
- **AWS S3 (스냅샷 백업)**:
    - DB에 에피소드가 저장될 때마다, 해당 시점까지의 전체 스토리를 `FullStoryDto` 형태의 JSON 파일로 변환하여 S3에 백업 스냅샷으로 업로드합니다.
    - 이는 데이터 유실 방지 및 외부 시스템과의 연동(예: 전체 스토리 다운로드)을 위한 보조적인 저장소 역할을 합니다.

### 2. 스토리 생성 및 편집 흐름
- **단계별 (Episode-by-Episode) 생성**:
    - `SequentialGenerationService`가 AI 서버와 통신하여 한 번에 하나의 에피소드를 생성합니다.
    - 에피소드가 생성되면 DB에 저장되고, `StoryCreation` 상태가 `AWAITING_USER_ACTION`으로 변경되어 사용자 개입을 기다립니다.
- **사용자 편집 기능**:
    - `StoryEditingService`와 `StoryEditingController`를 통해 사용자는 생성된 에피소드의 특정 노드(예: 텍스트)를 조회하고 수정할 수 있습니다.
    - **새로운 API 엔드포인트**:
        - `GET /api/story-edit/stories/{storyId}/episodes/{episodeOrder}`: 특정 에피소드의 상세 데이터 조회
        - `PUT /api/story-edit/nodes/{nodeId}`: 특정 노드의 텍스트 수정 (하위 트리 재생성 없음)
- **탑다운(Top-down) 하위 트리 재생성**:
    - 사용자가 특정 노드를 수정하고 `POST /api/story-edit/nodes/{nodeId}/regenerate` 엔드포인트를 호출하면, 해당 노드 아래의 모든 하위 스토리(서브트리)가 AI에 의해 새로운 내용에 맞춰 자동으로 재생성됩니다.
    - 이 과정에서 기존 하위 트리는 DB에서 삭제되고, AI가 생성한 새로운 하위 트리가 다시 DB에 저장되어 교체됩니다.

### 3. 주요 변경된/추가된 구성 요소
- **`src/main/java/com/story/game/story` 패키지**:
    - **`entity`**: `Episode`, `StoryNode`, `StoryChoice`, `EpisodeEnding` 엔티티 정의.
    - **`repository`**: 각 엔티티를 위한 Spring Data JPA 리포지토리.
    - **`mapper`**: `StoryMapper` 클래스를 통해 DTO와 엔티티 간 변환, DB에서 `FullStoryDto` 조립 로직 구현.
    - **`service`**: `StoryEditingService` (스토리 조회, 노드 수정, 하위 트리 재생성 로직).
    - **`controller`**: `StoryEditingController` (편집 관련 API 엔드포인트).
- **`SequentialGenerationService`**: S3 대신 DB를 중심으로 에피소드를 저장하고 관리하도록 로직 전면 개편. S3는 백업용으로 활용.
- **`StoryCreation` 엔티티**: `s3FileKey` 필드를 백업용 스냅샷의 키로 활용.
- **`StoryGenerationService`**: 더 이상 사용되지 않아 삭제됨.
이러한 변경을 통해 사용자는 AI가 생성하는 스토리에 더 깊이 개입하고, 원하는 방향으로 스토리를 이끌어갈 수 있게 됩니다.