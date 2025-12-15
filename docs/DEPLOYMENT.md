# EC2 배포 가이드

이 문서는 AWS EC2에 Spring Boot 애플리케이션을 Docker로 배포하는 방법을 설명합니다.

## 목차
1. [로컬 개발 환경](#로컬-개발-환경)
2. [EC2 배포 환경](#ec2-배포-환경)
3. [배포 단계](#배포-단계)

---

## 로컬 개발 환경

### 그냥 실행하기
```bash
# 기본 프로파일(dev)로 실행
./gradlew bootRun
```

기본 설정은 `dev` 프로파일입니다. (application.yml 참조)
- DB: localhost:3306
- AI Server: localhost:8000
- 환경변수는 `.env` 파일 또는 시스템 환경변수에서 읽어옵니다

### Docker Compose로 DB 실행
```bash
# MariaDB + LocalStack S3 실행
docker-compose up -d

# 애플리케이션 실행
./gradlew bootRun
```

---

## EC2 배포 환경

### 1. EC2 인스턴스 준비

#### 인스턴스 스펙 권장사항
- **타입**: t3.medium 이상 (2 vCPU, 4GB RAM)
- **OS**: Amazon Linux 2 또는 Ubuntu 22.04
- **스토리지**: 20GB 이상
- **보안 그룹**: 포트 8080, 22, 80, 443 오픈

#### 필수 소프트웨어 설치
```bash
# Docker 설치
sudo yum update -y  # Amazon Linux
sudo yum install -y docker
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker ec2-user

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# 재로그인 (docker 그룹 적용)
exit
```

### 2. 프로젝트 배포

#### 방법 1: GitHub에서 클론
```bash
# 프로젝트 클론
git clone https://github.com/your-org/story-backend.git
cd story-backend

# 환경변수 파일 생성
cp .env.example .env
vi .env  # 실제 운영 값으로 수정
```

#### 방법 2: JAR 파일 직접 업로드
```bash
# 로컬에서 빌드
./gradlew bootJar

# EC2로 업로드
scp -i your-key.pem build/libs/story-game-*.jar ec2-user@your-ec2-ip:/home/ec2-user/
```

### 3. 환경변수 설정

`.env` 파일을 생성하고 실제 운영 값을 입력합니다:

```bash
# .env 파일 생성
vi .env
```

```properties
# Spring Profile - 운영 환경
SPRING_PROFILES_ACTIVE=prod

# 데이터베이스 (RDS 또는 외부 DB)
DB_HOST=your-rds-endpoint.ap-northeast-2.rds.amazonaws.com
DB_PORT=3306
DB_NAME=story_game
DB_USERNAME=admin
DB_PASSWORD=your-secure-password

# AI 서버 (같은 VPC 또는 외부 서버)
AI_SERVER_URL=http://10.0.1.100:8000
RELAY_SERVER_URL=http://10.0.1.101:8081

# JWT 보안키 (256비트 이상)
JWT_SECRET=your-production-secret-key-must-be-at-least-256-bits-long

# AWS S3 (운영용 버킷)
AWS_S3_BUCKET=story-game-prod
AWS_S3_REGION=ap-northeast-2
AWS_ACCESS_KEY=AKIAXXXXXXXXX
AWS_SECRET_KEY=xxxxxxxxxxxxxxxxx

# CORS (프론트엔드 도메인)
CORS_ALLOWED_ORIGINS=https://yourdomain.com

# 기타 설정
SERVER_PORT=8080
JPA_DDL_AUTO=validate
LOG_LEVEL=INFO
```

### 4. Docker로 실행

#### 이미지 빌드 및 실행
```bash
# Docker 이미지 빌드
docker build -t story-backend .

# 컨테이너 실행
docker run -d \
  --name story-backend \
  -p 8080:8080 \
  --env-file .env \
  --restart unless-stopped \
  story-backend

# 로그 확인
docker logs -f story-backend
```

#### 또는 Docker Compose 사용
```bash
# docker-compose.prod.yml 생성
cat > docker-compose.prod.yml << 'EOF'
version: '3.8'

services:
  app:
    build: .
    container_name: story-backend
    ports:
      - "8080:8080"
    env_file:
      - .env
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
EOF

# 실행
docker-compose -f docker-compose.prod.yml up -d

# 로그 확인
docker-compose -f docker-compose.prod.yml logs -f
```

---

## 배포 단계 요약

### 로컬 개발자
```bash
# 1. 그냥 실행
./gradlew bootRun

# 끝!
```

### EC2 배포
```bash
# 1. EC2 접속
ssh -i your-key.pem ec2-user@your-ec2-ip

# 2. 프로젝트 준비
git clone https://github.com/your-org/story-backend.git
cd story-backend

# 3. 환경변수 설정
cp .env.example .env
vi .env  # 운영 값으로 수정

# 4. SPRING_PROFILES_ACTIVE=prod 확인
grep SPRING_PROFILES_ACTIVE .env

# 5. Docker로 실행
docker build -t story-backend .
docker run -d \
  --name story-backend \
  -p 8080:8080 \
  --env-file .env \
  --restart unless-stopped \
  story-backend

# 6. 확인
docker logs -f story-backend
curl http://localhost:8080/actuator/health
```

---

## 주요 차이점

| 항목 | 로컬 개발 | EC2 운영 |
|------|-----------|----------|
| 실행 명령 | `./gradlew bootRun` | `docker run --env-file .env` |
| 프로파일 | `dev` (기본) | `prod` (환경변수) |
| DB | localhost:3306 | RDS 엔드포인트 |
| AI 서버 | localhost:8000 | 실제 서버 IP |
| S3 버킷 | dev 버킷 | prod 버킷 |
| DDL Auto | update | validate |
| 로그 레벨 | DEBUG | INFO |

---

## 트러블슈팅

### 포트 충돌
```bash
# 포트 사용 확인
sudo netstat -tulpn | grep 8080

# 기존 컨테이너 종료
docker stop story-backend
docker rm story-backend
```

### 환경변수 확인
```bash
# 컨테이너 내부 환경변수 확인
docker exec story-backend env | grep SPRING_PROFILES_ACTIVE
```

### 로그 확인
```bash
# 실시간 로그
docker logs -f story-backend

# 최근 100줄
docker logs --tail 100 story-backend
```

### 컨테이너 재시작
```bash
docker restart story-backend
```

---

## 보안 체크리스트

- [ ] `.env` 파일이 `.gitignore`에 포함되어 있는지 확인
- [ ] JWT_SECRET을 운영용 강력한 키로 변경
- [ ] DB 비밀번호를 강력한 것으로 설정
- [ ] AWS 키가 노출되지 않았는지 확인
- [ ] EC2 보안 그룹에서 필요한 포트만 오픈
- [ ] RDS 보안 그룹에서 EC2만 접근 가능하도록 설정
- [ ] CORS 설정에서 실제 프론트엔드 도메인만 허용

---

## 참고 문서

- [AWS EC2 시작하기](https://docs.aws.amazon.com/ec2/)
- [Docker 공식 문서](https://docs.docker.com/)
- [Spring Boot Production](https://docs.spring.io/spring-boot/docs/current/reference/html/deployment.html)
