# 환경 변수 설정 가이드

## .env 파일 생성

1. `.env.example` 파일을 복사하여 `.env` 파일 생성:

```bash
# Windows
copy .env.example .env

# Linux/Mac
cp .env.example .env
```

2. `.env` 파일을 열어 실제 값으로 수정:

```bash
# 데이터베이스 설정
DB_HOST=localhost
DB_PORT=3306
DB_NAME=story_game
DB_USERNAME=root
DB_PASSWORD=your_real_password_here

# Python AI 서버 설정
AI_SERVER_URL=http://localhost:8000

# 서버 포트
SERVER_PORT=8080

# JPA 설정
JPA_DDL_AUTO=update

# 로그 레벨
LOG_LEVEL=INFO
```

## Spring Boot에서 .env 사용 방법

### 방법 1: IDE 설정 (IntelliJ IDEA)

1. **Run > Edit Configurations** 메뉴 선택
2. **Environment variables** 필드에 추가:
   ```
   DB_PASSWORD=your_password;AI_SERVER_URL=http://localhost:8000
   ```
3. 또는 **EnvFile** 플러그인 설치:
   - Settings > Plugins > "EnvFile" 검색 및 설치
   - Run Configuration에서 .env 파일 경로 지정

### 방법 2: 시스템 환경 변수

**Windows:**
```cmd
set DB_PASSWORD=your_password
set AI_SERVER_URL=http://localhost:8000
```

**Linux/Mac:**
```bash
export DB_PASSWORD=your_password
export AI_SERVER_URL=http://localhost:8000
```

### 방법 3: Gradle 실행 시 전달

```bash
# Windows
gradlew.bat bootRun -Dspring-boot.run.arguments="--spring.datasource.password=your_password --ai-server.url=http://localhost:8000"

# Linux/Mac
./gradlew bootRun --args='--spring.datasource.password=your_password --ai-server.url=http://localhost:8000'
```

### 방법 4: .env 파일 자동 로드 (권장)

`build.gradle`에 다음 플러그인 추가:

```gradle
plugins {
    id "com.github.johnrengelman.processes" version "0.5.0"
    id "org.springframework.boot.experimental.thin-launcher" version "1.0.28.RELEASE"
}
```

또는 프로젝트에 `spring-dotenv` 의존성 추가:

```gradle
implementation 'me.paulschwarz:spring-dotenv:2.5.4'
```

## 환경별 설정

### 개발 환경 (.env.development)

```bash
DB_HOST=localhost
DB_PASSWORD=dev_password
JPA_DDL_AUTO=update
LOG_LEVEL=DEBUG
```

### 운영 환경 (.env.production)

```bash
DB_HOST=production-db.example.com
DB_PASSWORD=prod_secure_password
JPA_DDL_AUTO=validate
LOG_LEVEL=WARN
```

## 보안 주의사항

1. ⚠️ **절대로 .env 파일을 Git에 커밋하지 마세요!**
   - `.gitignore`에 `.env`가 포함되어 있는지 확인

2. 🔒 **비밀번호와 API 키는 안전하게 관리**
   - 운영 환경에서는 AWS Secrets Manager, Azure Key Vault 등 사용 권장

3. 📝 **.env.example은 커밋해도 됩니다**
   - 실제 비밀번호가 아닌 예시 값만 포함

## 환경 변수 목록

| 변수명 | 설명 | 기본값 | 필수 |
|--------|------|--------|------|
| `DB_HOST` | 데이터베이스 호스트 | localhost | ❌ |
| `DB_PORT` | 데이터베이스 포트 | 3306 | ❌ |
| `DB_NAME` | 데이터베이스 이름 | story_game | ❌ |
| `DB_USERNAME` | 데이터베이스 사용자명 | root | ❌ |
| `DB_PASSWORD` | 데이터베이스 비밀번호 | password | ✅ |
| `AI_SERVER_URL` | Python AI 서버 URL | http://localhost:8000 | ❌ |
| `SERVER_PORT` | Spring Boot 서버 포트 | 8080 | ❌ |
| `JPA_DDL_AUTO` | JPA DDL 모드 | update | ❌ |
| `LOG_LEVEL` | 로그 레벨 | INFO | ❌ |
| `CORS_ALLOWED_ORIGINS` | CORS 허용 오리진 | localhost:3000,5173 | ❌ |

## 문제 해결

### 환경 변수가 적용되지 않을 때

1. IDE를 재시작
2. Gradle 캐시 삭제: `./gradlew clean`
3. 환경 변수 확인:
   ```bash
   # Windows
   echo %DB_PASSWORD%

   # Linux/Mac
   echo $DB_PASSWORD
   ```

### application.yml에서 직접 값 확인

서버 실행 로그에서 실제 적용된 값 확인:
```
spring.datasource.url=jdbc:mariadb://localhost:3306/story_game
```
