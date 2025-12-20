# 트러블슈팅 가이드

## 파일 업로드 실패: "파일 분석에 실패했습니다"

이 에러는 백엔드에서 AI 서버와 통신할 때 발생하는 문제입니다.

### 1단계: 모든 서버가 실행 중인지 확인

#### 필요한 서버
1. **AI 서버** (포트 8000)
2. **relay-server** (포트 8081)
3. **story-backend** (포트 8080)

#### 확인 방법
```bash
# Windows
netstat -ano | findstr :8000
netstat -ano | findstr :8081
netstat -ano | findstr :8080

# 포트가 사용 중이어야 함
```

### 2단계: AI 서버 실행

```bash
cd "C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\AI"
python api.py
```

**기대 출력:**
```
INFO:     Uvicorn running on http://0.0.0.0:8000 (Press CTRL+C to quit)
INFO:     Started reloader process
INFO:     Started server process
INFO:     Waiting for application startup.
INFO:     Application startup complete.
```

### 3단계: relay-server 실행

```bash
cd "C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\relay-server"
./gradlew bootRun
```

**기대 출력:**
```
Started RelayServerApplication in X.XXX seconds
```

### 4단계: story-backend 실행

```bash
cd "C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\story-backend"
./gradlew bootRun
```

**기대 출력:**
```
Started StoryGameApplication in X.XXX seconds
```

### 5단계: 연결 테스트

#### AI 서버 헬스 체크
```bash
curl http://localhost:8000/health
```
**기대 응답:** `{"status":"healthy"}`

#### relay-server 헬스 체크
```bash
curl http://localhost:8081/ai/health
```
**기대 응답:**
```json
{
  "status": "healthy",
  "relayServer": "up",
  "aiServers": {
    "analysisAi": {"status": "up"},
    "imageGenerationAi": {"status": "down"},
    "ragAi": {"status": "down"}
  }
}
```

#### story-backend 헬스 체크
```bash
curl http://localhost:8080/actuator/health
```
**기대 응답:** `{"status":"UP"}`

### 6단계: 환경변수 확인

#### relay-server의 .env 파일
```bash
cat C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\relay-server\.env
```

**필수 설정:**
```properties
AI_ANALYSIS_URL=http://localhost:8000
```

#### story-backend의 .env 파일
```bash
cat C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\story-backend\.env
```

**필수 설정:**
```properties
RELAY_SERVER_URL=http://localhost:8081
```

### 7단계: 백엔드 로그 확인

story-backend 실행 창에서 에러 로그를 확인하세요:

```
=== RuntimeException ===
Message: Analysis failed: Connection refused
```

이런 메시지가 나오면 relay-server나 AI 서버가 실행되지 않은 것입니다.

---

## 일반적인 문제들

### 문제 1: "Connection refused"
**원인:** relay-server 또는 AI 서버가 실행되지 않음
**해결:** 위 2-3단계 실행

### 문제 2: "Read timed out"
**원인:** AI 서버 응답 시간 초과
**해결:**
1. AI 서버가 정상 작동하는지 확인
2. 타임아웃 설정 증가 (relay-server의 application.yml)

### 문제 3: "No response from analysis AI server"
**원인:** AI 서버가 응답하지 않음
**해결:**
1. AI 서버 로그 확인
2. Python 환경 및 의존성 확인
```bash
cd C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\AI
pip install -r requirements.txt
```

### 문제 4: CORS 에러
**원인:** 프론트엔드 URL이 허용 목록에 없음
**해결:** story-backend의 .env 파일에 추가
```properties
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```

---

## 디버깅 체크리스트

- [ ] AI 서버 실행 중 (포트 8000)
- [ ] relay-server 실행 중 (포트 8081)
- [ ] story-backend 실행 중 (포트 8080)
- [ ] AI 서버 헬스 체크 성공
- [ ] relay-server 헬스 체크 성공
- [ ] story-backend 헬스 체크 성공
- [ ] relay-server .env 파일에 AI_ANALYSIS_URL 설정됨
- [ ] story-backend .env 파일에 RELAY_SERVER_URL 설정됨
- [ ] CORS 설정 확인
- [ ] 백엔드 로그에서 구체적인 에러 확인

---

## 빠른 시작 스크립트

### start-all.bat (Windows)
```batch
@echo off
echo Starting AI Server...
start cmd /k "cd C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\AI && python api.py"

timeout /t 5

echo Starting relay-server...
start cmd /k "cd C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\relay-server && gradlew.bat bootRun"

timeout /t 10

echo Starting story-backend...
start cmd /k "cd C:\Users\user\OneDrive\바탕 화면\최종 프로젝트\story-backend && gradlew.bat bootRun"

echo All servers started!
```

사용 방법:
```bash
# 위 내용을 start-all.bat로 저장하고 실행
start-all.bat
```

---

## 여전히 문제가 있다면?

1. **백엔드 로그 전체 복사**
2. **프론트엔드 콘솔 에러 전체 복사**
3. **각 서버의 헬스 체크 결과 복사**
4. **환경변수 설정 확인**

이 정보를 가지고 문제를 진단할 수 있습니다.
