# Story Game Backend - Documentation

이 폴더는 Story Game Backend 프로젝트의 모든 문서를 포함합니다.

## 📖 문서 목록

### API 가이드

#### [STORY_GENERATION_API.md](STORY_GENERATION_API.md)
**9단계 세분화 스토리 생성 프로세스**

새로운 스토리 생성 API의 상세 가이드입니다. 소설 업로드부터 완성된 스토리 생성까지 9단계로 나뉘어 있습니다.

**주요 내용:**
- 단계별 API 엔드포인트
- 요청/응답 형식
- 상태 전이 다이어그램
- 에러 처리 방법

**사용 대상:** 프론트엔드 개발자, API 사용자

---

#### [FRONTEND_INTEGRATION_GUIDE.md](FRONTEND_INTEGRATION_GUIDE.md)
**프론트엔드 S3 파일 업로드 통합 가이드**

프론트엔드에서 S3로 파일을 직접 업로드하는 방법과 백엔드 API 연동 가이드입니다.

**주요 내용:**
- Pre-signed URL 사용법
- 파일 타입별 업로드 (스토리/이미지/동영상)
- 파일 검증 및 에러 처리
- 완전한 통합 예제 (React/Vue)

**사용 대상:** 프론트엔드 개발자

---

### 시스템 통합

#### [AI_SERVER_S3_INTEGRATION.md](AI_SERVER_S3_INTEGRATION.md)
**AI 서버와 S3 직접 연동 가이드**

AI 서버가 S3에서 직접 파일을 읽고 쓰도록 하는 통합 가이드입니다.

**주요 내용:**
- 두 가지 S3 연동 방식 비교
- Pre-signed URL을 통한 직접 업로드
- AI 서버 구현 가이드 (Python)
- 플로우 다이어그램
- 성능 최적화 전략

**사용 대상:** AI 서버 개발자, 백엔드 개발자

**핵심 개선점:**
- 🚀 네트워크 트래픽 50% 감소
- 💾 백엔드 메모리 사용량 대폭 감소
- ⚡ 전체 처리 시간 단축

---

### 설정 및 배포

#### [ENV_SETUP.md](ENV_SETUP.md)
**환경 설정 가이드**

데이터베이스, AWS S3, 환경 변수 등 시스템 설정에 대한 상세 가이드입니다.

**주요 내용:**
- 데이터베이스 설정 (MariaDB)
- AWS S3 설정
- 환경 변수 설정
- 운영 환경 배포 가이드

**사용 대상:** 백엔드 개발자, DevOps

---

#### [GEMINI.md](GEMINI.md)
**Gemini AI 통합 설정**

Google Gemini AI를 사용하는 경우의 설정 가이드입니다.

**주요 내용:**
- Gemini API 키 설정
- AI 서버와의 통합 방법
- 사용 예제

**사용 대상:** AI 서버 개발자

---

## 🗂️ 문서 구조

```
docs/
├── README.md                          # 이 파일
├── STORY_GENERATION_API.md            # API 가이드
├── FRONTEND_INTEGRATION_GUIDE.md      # 프론트엔드 통합
├── AI_SERVER_S3_INTEGRATION.md        # AI 서버 S3 통합
├── ENV_SETUP.md                       # 환경 설정
└── GEMINI.md                          # Gemini AI 설정
```

---

## 📋 빠른 시작 가이드

### 1. 처음 시작하는 경우

1. **[ENV_SETUP.md](ENV_SETUP.md)** - 환경 설정
2. **[STORY_GENERATION_API.md](STORY_GENERATION_API.md)** - API 이해
3. **[FRONTEND_INTEGRATION_GUIDE.md](FRONTEND_INTEGRATION_GUIDE.md)** - 프론트엔드 통합

### 2. 프론트엔드 개발자

1. **[FRONTEND_INTEGRATION_GUIDE.md](FRONTEND_INTEGRATION_GUIDE.md)** - 파일 업로드 및 API 연동
2. **[STORY_GENERATION_API.md](STORY_GENERATION_API.md)** - API 상세 명세

### 3. AI 서버 개발자

1. **[AI_SERVER_S3_INTEGRATION.md](AI_SERVER_S3_INTEGRATION.md)** - S3 직접 연동
2. **[GEMINI.md](GEMINI.md)** - Gemini 설정 (선택)

### 4. DevOps / 배포 담당자

1. **[ENV_SETUP.md](ENV_SETUP.md)** - 환경 설정 및 배포

---

## 🔗 관련 링크

- [메인 README](../README.md)
- [프로젝트 인스트럭션](../CLAUDE.md)
- [Swagger UI](http://localhost:8080/swagger-ui.html) (서버 실행 후)

---

## 📝 문서 작성 규칙

문서를 추가하거나 수정할 때는 다음 규칙을 따라주세요:

1. **파일명**: `UPPERCASE_WITH_UNDERSCORES.md` 형식 사용
2. **제목**: 명확하고 구체적으로
3. **대상 독자**: 각 문서 상단에 대상 독자 명시
4. **예제 코드**: 실행 가능한 완전한 예제 제공
5. **다이어그램**: 복잡한 플로우는 ASCII 다이어그램으로 시각화
6. **업데이트**: 코드 변경 시 관련 문서도 함께 업데이트

---

**Last Updated:** 2025-11-26
