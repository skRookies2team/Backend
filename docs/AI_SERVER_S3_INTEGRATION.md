# AI 서버 S3 통합 작업 가이드

## 배경

현재 백엔드가 S3에서 파일을 다운로드해서 AI 서버로 전송하는 비효율적인 구조를 개선하기 위해,
**AI 서버가 S3에서 직접 파일을 다운로드**하고 **S3에 직접 업로드**하도록 변경합니다.

---

## 📋 두 가지 S3 연동 방식

Backend는 **두 가지 S3 연동 방식**을 지원합니다:

### 방식 1: AI 서버 직접 업로드 (Pre-signed URL)
**StoryGenerationService** - 레거시 API

```
1. Frontend → Backend: 소설 텍스트 전송
2. Backend → S3: Pre-signed URL 생성
3. Backend → AI Server: Pre-signed URL 포함하여 요청
4. AI Server: 스토리 생성
5. AI Server → S3: Pre-signed URL로 직접 업로드 ✅
6. AI Server → Backend: 성공 응답 (메타데이터만)
7. Backend → DB: fileKey 저장
```

**장점**: AI 서버가 직접 S3에 업로드하므로 Backend 메모리 부담 없음

### 방식 2: AI 서버 직접 업로드 (Pre-signed URL - 권장) ⭐
**StoryManagementService** - 신규 세분화 프로세스

```
1. Frontend → S3: 소설 파일 업로드 (Pre-signed URL)
2. Frontend → Backend: fileKey 전송
3. Backend → S3: 결과 업로드용 Pre-signed URL 생성
4. Backend → AI Server: fileKey + Pre-signed URL 전송
5. AI Server → S3: fileKey로 소설 다운로드 ✅
6. AI Server: 스토리 생성
7. AI Server → S3: Pre-signed URL로 결과 직접 업로드 ✅
8. AI Server → Backend: 완료 알림 (메타데이터만)
9. Backend → DB: fileKey 저장
```

**장점**: 큰 JSON 데이터가 AI 서버 → Backend를 거치지 않음 (네트워크 효율 최대)

---

## 🔄 상세 플로우 다이어그램

### 방식 1: StoryGenerationService (Pre-signed URL)

```
┌──────────┐                ┌──────────┐                ┌──────────┐                ┌──────────┐
│ Frontend │                │ Backend  │                │AI Server │                │   S3     │
└────┬─────┘                └────┬─────┘                └────┬─────┘                └────┬─────┘
     │                           │                           │                           │
     │ POST /api/game/stories/   │                           │                           │
     │ generate (novel_text)     │                           │                           │
     ├──────────────────────────>│                           │                           │
     │                           │                           │                           │
     │                           │ generatePresignedUploadUrl│                           │
     │                           │ (fileKey)                 │                           │
     │                           ├──────────────────────────────────────────────────────>│
     │                           │                           │                           │
     │                           │<──────────────────────────────────────────────────────┤
     │                           │ presignedUrl (15분 유효)   │                           │
     │                           │                           │                           │
     │                           │ POST /generate            │                           │
     │                           │ {                         │                           │
     │                           │   novel_text,             │                           │
     │                           │   selected_gauge_ids,     │                           │
     │                           │   file_key,               │                           │
     │                           │   s3_upload_url ✅        │                           │
     │                           │ }                         │                           │
     │                           ├──────────────────────────>│                           │
     │                           │                           │                           │
     │                           │                           │ [스토리 생성 중...]        │
     │                           │                           │                           │
     │                           │                           │ PUT {presignedUrl}        │
     │                           │                           │ (스토리 JSON 업로드)       │
     │                           │                           ├──────────────────────────>│
     │                           │                           │                           │
     │                           │                           │<──────────────────────────┤
     │                           │                           │ 200 OK                    │
     │                           │                           │                           │
     │                           │ {                         │                           │
     │                           │   status: "success",      │                           │
     │                           │   data: {metadata}        │                           │
     │                           │ }                         │                           │
     │                           │<──────────────────────────┤                           │
     │                           │                           │                           │
     │                           │ DB 저장 (fileKey)          │                           │
     │                           │                           │                           │
     │<──────────────────────────┤                           │                           │
     │ StoryData (id, fileKey)   │                           │                           │
     │                           │                           │                           │
```

### 방식 2: StoryManagementService (세분화 프로세스)

```
┌──────────┐                ┌──────────┐                ┌──────────┐                ┌──────────┐
│ Frontend │                │ Backend  │                │AI Server │                │   S3     │
└────┬─────┘                └────┬─────┘                └────┬─────┘                └────┬─────┘
     │                           │                           │                           │
     │ 1️⃣ GET /api/upload/       │                           │                           │
     │    presigned-url          │                           │                           │
     ├──────────────────────────>│                           │                           │
     │                           │                           │                           │
     │<──────────────────────────┤                           │                           │
     │ {url, fileKey}            │                           │                           │
     │                           │                           │                           │
     │ 2️⃣ PUT {presignedUrl}      │                           │                           │
     │    (소설 txt 파일)         │                           │                           │
     ├──────────────────────────────────────────────────────────────────────────────────>│
     │                           │                           │                           │
     │<──────────────────────────────────────────────────────────────────────────────────┤
     │ 200 OK                    │                           │                           │
     │                           │                           │                           │
     │ 3️⃣ POST /api/stories/      │                           │                           │
     │    upload-from-s3         │                           │                           │
     │    {title, fileKey}       │                           │                           │
     ├──────────────────────────>│                           │                           │
     │                           │                           │                           │
     │                           │ POST /analyze-from-s3     │                           │
     │                           │ {file_key, bucket}        │                           │
     │                           ├──────────────────────────>│                           │
     │                           │                           │                           │
     │                           │                           │ GET Object (fileKey)      │
     │                           │                           ├──────────────────────────>│
     │                           │                           │                           │
     │                           │                           │<──────────────────────────┤
     │                           │                           │ 소설 텍스트                │
     │                           │                           │                           │
     │                           │                           │ [AI 분석 중...]           │
     │                           │                           │                           │
     │                           │ {summary, characters,     │                           │
     │                           │  gauges}                  │                           │
     │                           │<──────────────────────────┤                           │
     │                           │                           │                           │
     │<──────────────────────────┤                           │                           │
     │ {storyId, status}         │                           │                           │
     │                           │                           │                           │
     │ 4️⃣ POST /api/stories/{id}/ │                           │                           │
     │    gauges/select          │                           │                           │
     ├──────────────────────────>│                           │                           │
     │                           │                           │                           │
     │ 5️⃣ POST /api/stories/{id}/ │                           │                           │
     │    config                 │                           │                           │
     ├──────────────────────────>│                           │                           │
     │                           │                           │                           │
     │ 6️⃣ POST /api/stories/{id}/ │                           │                           │
     │    generate               │                           │                           │
     ├──────────────────────────>│                           │                           │
     │                           │                           │                           │
     │                           │ generatePresignedUploadUrl│                           │
     │                           │ (결과 파일용)              │                           │
     │                           ├──────────────────────────────────────────────────────>│
     │                           │                           │                           │
     │                           │<──────────────────────────────────────────────────────┤
     │                           │ presignedUrl (15분 유효)   │                           │
     │                           │                           │                           │
     │                           │ POST /generate-from-s3    │                           │
     │                           │ {                         │                           │
     │                           │   file_key,               │                           │
     │                           │   bucket,                 │                           │
     │                           │   selected_gauge_ids,     │                           │
     │                           │   num_episodes, ...       │                           │
     │                           │   s3_upload_url, ✅       │                           │
     │                           │   s3_file_key ✅          │                           │
     │                           │ }                         │                           │
     │                           ├──────────────────────────>│                           │
     │                           │                           │                           │
     │                           │                           │ GET Object (입력 fileKey) │
     │                           │                           ├──────────────────────────>│
     │                           │                           │                           │
     │                           │                           │<──────────────────────────┤
     │                           │                           │ 소설 텍스트                │
     │                           │                           │                           │
     │                           │                           │ [스토리 생성 중...]        │
     │                           │                           │                           │
     │                           │                           │ PUT {presignedUrl}        │
     │                           │                           │ (스토리 JSON 업로드) ✅     │
     │                           │                           ├──────────────────────────>│
     │                           │                           │                           │
     │                           │                           │<──────────────────────────┤
     │                           │                           │ 200 OK                    │
     │                           │                           │                           │
     │                           │ {                         │                           │
     │                           │   status: "success",      │                           │
     │                           │   file_key: "stories/..", │                           │
     │                           │   metadata: {...}         │                           │
     │                           │ }                         │                           │
     │                           │<──────────────────────────┤                           │
     │                           │                           │                           │
     │                           │ DB 저장 (fileKey)          │                           │
     │                           │                           │                           │
     │<──────────────────────────┤                           │                           │
     │ {storyId, status}         │                           │                           │
     │                           │                           │                           │
```

---

## 🔧 AI 서버 작업 내용

### 1. 환경 설정

#### requirements.txt에 추가
```txt
boto3==1.34.0
```

#### 환경 변수 설정
```bash
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_REGION="ap-northeast-2"
export AWS_S3_BUCKET="story-game-bucket"
```

### 2. S3 클라이언트 초기화

```python
# main.py 또는 app.py 상단에 추가

import boto3
import os
import requests
from botocore.exceptions import ClientError

# S3 클라이언트 초기화
s3_client = boto3.client(
    's3',
    region_name=os.getenv('AWS_REGION', 'ap-northeast-2'),
    aws_access_key_id=os.getenv('AWS_ACCESS_KEY_ID'),
    aws_secret_access_key=os.getenv('AWS_SECRET_ACCESS_KEY')
)

def download_from_s3(file_key: str, bucket: str = None) -> str:
    """S3에서 파일을 다운로드하여 텍스트로 반환"""
    if bucket is None:
        bucket = os.getenv('AWS_S3_BUCKET', 'story-game-bucket')

    try:
        response = s3_client.get_object(Bucket=bucket, Key=file_key)
        content = response['Body'].read().decode('utf-8')
        return content
    except ClientError as e:
        if e.response['Error']['Code'] == 'NoSuchKey':
            raise HTTPException(status_code=404, detail=f"File not found in S3: {file_key}")
        else:
            raise HTTPException(status_code=500, detail=f"S3 error: {str(e)}")

def upload_to_s3_via_presigned_url(presigned_url: str, content: str) -> bool:
    """Pre-signed URL을 사용하여 S3에 업로드"""
    try:
        response = requests.put(
            presigned_url,
            data=content.encode('utf-8'),
            headers={'Content-Type': 'application/json'}
        )
        return response.status_code == 200
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"S3 upload failed: {str(e)}")
```

### 3. 엔드포인트 구현

#### 3-1. 소설 분석 (S3 방식)

```python
from pydantic import BaseModel
from typing import Optional

class AnalyzeFromS3Request(BaseModel):
    file_key: str
    bucket: Optional[str] = "story-game-bucket"

@app.post("/analyze-from-s3")
async def analyze_novel_from_s3(request: AnalyzeFromS3Request):
    """
    S3에서 소설 파일을 다운로드하여 분석

    Request:
    {
        "file_key": "uploads/abc123_novel.txt",
        "bucket": "story-game-bucket"
    }

    Response:
    {
        "summary": "소설 요약 500자...",
        "characters": [...],
        "gauges": [...]
    }
    """
    try:
        # S3에서 파일 다운로드
        novel_text = download_from_s3(request.file_key, request.bucket)

        # 기존 분석 로직 재사용
        summary = await analyze_summary(novel_text)
        characters = await extract_characters(novel_text)
        gauges = await suggest_gauges(novel_text)

        return {
            "summary": summary,
            "characters": characters,
            "gauges": gauges
        }

    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Analysis failed: {str(e)}")
```

#### 3-2. 스토리 생성 (S3 방식 - Pre-signed URL 사용) ⭐ 권장

```python
class GenerateFromS3Request(BaseModel):
    file_key: Optional[str] = None
    bucket: Optional[str] = "story-game-bucket"
    novel_text: Optional[str] = None  # S3가 아닌 경우 사용
    selected_gauge_ids: list[str]
    num_episodes: int
    max_depth: int
    ending_config: dict
    num_episode_endings: int
    s3_upload_url: Optional[str] = None  # Pre-signed URL ✅
    s3_file_key: Optional[str] = None    # 업로드할 파일의 키 ✅

@app.post("/generate-from-s3")
async def generate_story_from_s3(request: GenerateFromS3Request):
    """
    S3에서 소설 파일을 다운로드하여 스토리 생성
    AI 서버가 결과를 S3에 직접 업로드 (권장) 또는 Backend로 반환 (레거시)

    Request:
    {
        "file_key": "uploads/abc123_novel.txt",
        "bucket": "story-game-bucket",
        "selected_gauge_ids": ["civilization", "unity"],
        "num_episodes": 3,
        "max_depth": 3,
        "ending_config": {
            "happy": 2,
            "tragic": 1,
            "neutral": 1,
            "open": 1,
            "bad": 0
        },
        "num_episode_endings": 3,
        "s3_upload_url": "https://s3...presigned-url",  // 새 방식
        "s3_file_key": "stories/xyz789.json"            // 새 방식
    }

    Response (새 방식 - Pre-signed URL):
    {
        "status": "success",
        "message": "Story generated and uploaded to S3",
        "file_key": "stories/xyz789.json",
        "metadata": {
            "total_episodes": 3,
            "total_nodes": 150,
            "total_gauges": 2
        }
    }

    Response (레거시 - 전체 JSON 반환):
    {
        "status": "success",
        "message": "Story generated successfully",
        "data": {
            "metadata": {...},
            "context": {...},
            "episodes": [...]
        }
    }
    """
    try:
        # S3에서 파일 다운로드 (file_key가 있으면 S3, 없으면 novel_text 사용)
        if request.file_key:
            novel_text = download_from_s3(request.file_key, request.bucket)
        elif request.novel_text:
            novel_text = request.novel_text
        else:
            raise HTTPException(status_code=400, detail="Either file_key or novel_text is required")

        # 기존 생성 로직 재사용
        story_data = await generate_story(
            novel_text=novel_text,
            selected_gauge_ids=request.selected_gauge_ids,
            num_episodes=request.num_episodes,
            max_depth=request.max_depth,
            ending_config=request.ending_config,
            num_episode_endings=request.num_episode_endings
        )

        # 새 방식: Pre-signed URL이 있으면 S3에 직접 업로드 ✅
        if request.s3_upload_url and request.s3_file_key:
            import json
            story_json = json.dumps(story_data, ensure_ascii=False, indent=2)

            upload_success = upload_to_s3_via_presigned_url(
                request.s3_upload_url,
                story_json
            )

            if not upload_success:
                raise HTTPException(status_code=500, detail="Failed to upload to S3")

            # 메타데이터만 반환 (전체 JSON은 S3에 업로드됨)
            return {
                "status": "success",
                "message": "Story generated and uploaded to S3",
                "file_key": request.s3_file_key,
                "metadata": {
                    "total_episodes": story_data.get("metadata", {}).get("total_episodes"),
                    "total_nodes": story_data.get("metadata", {}).get("total_nodes"),
                    "total_gauges": story_data.get("metadata", {}).get("total_gauges")
                }
            }
        else:
            # 레거시 방식: 전체 스토리 JSON을 Backend로 반환
            return {
                "status": "success",
                "message": "Story generated successfully",
                "data": story_data
            }

    except HTTPException:
        raise
    except Exception as e:
        return {
            "status": "error",
            "message": str(e),
            "data": None
        }
```

#### 3-3. 스토리 생성 (Pre-signed URL 방식)

```python
class GenerateStoryRequest(BaseModel):
    novel_text: str
    selected_gauge_ids: list[str]
    num_episodes: int
    max_depth: int
    ending_config: dict
    num_episode_endings: int
    file_key: Optional[str] = None          # Backend가 생성한 fileKey
    s3_upload_url: Optional[str] = None     # Pre-signed URL ✅

@app.post("/generate")
async def generate_story(request: GenerateStoryRequest):
    """
    스토리 생성 후 AI 서버가 직접 S3에 업로드

    Request:
    {
        "novel_text": "...",
        "selected_gauge_ids": ["civilization", "unity"],
        "num_episodes": 3,
        "max_depth": 3,
        "ending_config": {...},
        "num_episode_endings": 3,
        "file_key": "stories/xyz789.json",
        "s3_upload_url": "https://story-game-bucket.s3.amazonaws.com/..."
    }

    Response:
    {
        "status": "success",
        "message": "Story generated and uploaded to S3",
        "data": {
            "metadata": {...}  # 메타데이터만 반환 (전체 JSON은 S3에 있음)
        }
    }
    """
    try:
        # 스토리 생성
        story_data = await generate_story_internal(
            novel_text=request.novel_text,
            selected_gauge_ids=request.selected_gauge_ids,
            num_episodes=request.num_episodes,
            max_depth=request.max_depth,
            ending_config=request.ending_config,
            num_episode_endings=request.num_episode_endings
        )

        # Pre-signed URL이 있으면 S3에 직접 업로드
        if request.s3_upload_url:
            import json
            story_json = json.dumps(story_data, ensure_ascii=False, indent=2)

            upload_success = upload_to_s3_via_presigned_url(
                request.s3_upload_url,
                story_json
            )

            if not upload_success:
                raise HTTPException(status_code=500, detail="Failed to upload to S3")

            # 메타데이터만 반환 (전체 JSON은 S3에 업로드됨)
            return {
                "status": "success",
                "message": "Story generated and uploaded to S3",
                "data": {
                    "metadata": story_data.get("metadata")
                }
            }
        else:
            # Pre-signed URL이 없으면 전체 데이터 반환 (레거시 방식)
            return {
                "status": "success",
                "message": "Story generated successfully",
                "data": story_data
            }

    except HTTPException:
        raise
    except Exception as e:
        return {
            "status": "error",
            "message": str(e),
            "data": None
        }
```

### 4. 기존 엔드포인트 유지

**중요: 기존 엔드포인트는 그대로 유지**하세요.
하위 호환성을 위해 세 가지 방식 모두 지원합니다:

- `/analyze` - 기존 방식 (텍스트 직접 전송)
- `/analyze-from-s3` - 새 방식 (S3 fileKey 전송)
- `/generate` - 레거시 방식 (텍스트 전송) + **Pre-signed URL 업로드 지원**
- `/generate-from-s3` - 새 방식 (S3 fileKey 전송)

---

## 📊 Backend 구현 현황

### ✅ 방식 1: StoryGenerationService (Pre-signed URL)

**파일**: `StoryGenerationService.java`

```java
// 1. Pre-signed URL 생성
String fileKey = "stories/" + UUID.randomUUID().toString() + ".json";
S3Service.PresignedUrlInfo presignedUrlInfo =
    s3Service.generatePresignedUploadUrl(fileKey);

// 2. AI 서버 요청 (s3_upload_url 포함)
StoryGenerationRequestDto aiRequest = StoryGenerationRequestDto.builder()
    .novelText(request.getNovelText())
    .selectedGaugeIds(request.getSelectedGaugeIds())
    .numEpisodes(request.getNumEpisodes())
    .maxDepth(request.getMaxDepth())
    .endingConfig(request.getEndingConfig())
    .numEpisodeEndings(request.getNumEpisodeEndings())
    .fileKey(presignedUrlInfo.getFileKey())      // ✅
    .s3UploadUrl(presignedUrlInfo.getUrl())      // ✅
    .build();

// 3. AI 서버 호출
response = aiServerWebClient.post()
    .uri("/generate")
    .bodyValue(aiRequest)
    .retrieve()
    .bodyToMono(StoryGenerationResponseDto.class)
    .block();

// 4. DB에 fileKey만 저장 (JSON은 이미 S3에 있음)
StoryData storyData = StoryData.builder()
    .title(request.getTitle())
    .description(request.getDescription())
    .storyFileKey(fileKey)  // AI 서버가 업로드한 파일
    .totalEpisodes(response.getData().getMetadata().getTotalEpisodes())
    .totalNodes(response.getData().getMetadata().getTotalNodes())
    .build();
```

**API 엔드포인트**: `POST /api/game/stories/generate`

### ✅ 방식 2: StoryManagementService (세분화 프로세스) ⭐ 최신

**파일**: `StoryManagementService.java`

```java
// 1. 분석 단계 - S3에서 다운로드
NovelAnalysisRequestDto aiRequest = NovelAnalysisRequestDto.builder()
    .fileKey(fileKey)
    .bucket("story-game-bucket")
    .build();

response = aiServerWebClient.post()
    .uri("/analyze-from-s3")  // S3 전용 엔드포인트
    .bodyValue(aiRequest)
    .retrieve()
    .bodyToMono(NovelAnalysisResponseDto.class)
    .block();

// 2. 생성 단계 - Pre-signed URL 생성 ✅
String resultFileKey = "stories/" + UUID.randomUUID().toString() + ".json";
String s3UploadUrl = s3Service.generatePresignedUploadUrl(resultFileKey).getUrl();

// 3. AI 서버 요청 (Pre-signed URL 포함) ✅
if (storyCreation.getS3FileKey() != null) {
    requestBuilder
        .fileKey(storyCreation.getS3FileKey())
        .bucket("story-game-bucket")
        .s3UploadUrl(s3UploadUrl)      // ✅ 추가
        .s3FileKey(resultFileKey);      // ✅ 추가
    aiEndpoint = "/generate-from-s3";
}

response = aiServerWebClient.post()
    .uri(aiEndpoint)
    .bodyValue(request)
    .retrieve()
    .bodyToMono(StoryGenerationResponseDto.class)
    .block();

// 4. 응답 처리 (새 방식 vs 레거시) ✅
String finalFileKey;
Integer totalEpisodes;
Integer totalNodes;

if (response.getFileKey() != null && response.getMetadata() != null) {
    // 새 방식: AI 서버가 S3에 직접 업로드 완료
    log.info("AI server uploaded result to S3 directly");
    finalFileKey = response.getFileKey();
    totalEpisodes = response.getMetadata().getTotalEpisodes();
    totalNodes = response.getMetadata().getTotalNodes();
} else if (response.getData() != null) {
    // 레거시 방식: Backend가 S3에 업로드
    log.warn("Using legacy mode - Backend uploading to S3");
    String storyJson = objectMapper.writeValueAsString(response.getData());
    finalFileKey = "stories/" + UUID.randomUUID().toString() + ".json";
    s3Service.uploadFile(finalFileKey, storyJson);
    totalEpisodes = response.getData().getMetadata().getTotalEpisodes();
    totalNodes = response.getData().getMetadata().getTotalNodes();
} else {
    throw new RuntimeException("Invalid AI server response");
}

// 5. DB에 저장
StoryData storyData = StoryData.builder()
    .title(storyCreation.getTitle())
    .description(storyCreation.getDescription())
    .storyFileKey(finalFileKey)
    .totalEpisodes(totalEpisodes)
    .totalNodes(totalNodes)
    .build();
```

**API 엔드포인트**: `POST /api/stories/{id}/generate`

**주요 개선점**:
- ✅ AI 서버가 S3에 직접 업로드 (네트워크 효율 향상)
- ✅ 큰 JSON 데이터가 Backend를 거치지 않음 (메모리 절약)
- ✅ 레거시 방식도 지원 (하위 호환성)

---

## 🔐 AWS Credentials 설정

AI 서버와 Backend가 **같은 AWS 계정**의 S3에 접근해야 합니다.

### Backend 설정 (application.yml)
```yaml
aws:
  s3:
    bucket: story-game-bucket
    region: ap-northeast-2
  credentials:
    access-key: ${AWS_ACCESS_KEY}
    secret-key: ${AWS_SECRET_KEY}
```

### AI 서버 설정 (.env 또는 환경변수)
```bash
AWS_ACCESS_KEY_ID=same-as-backend
AWS_SECRET_ACCESS_KEY=same-as-backend
AWS_REGION=ap-northeast-2
AWS_S3_BUCKET=story-game-bucket
```

**⚠️ 주의: 반드시 같은 credentials를 사용해야 합니다!**

---

## 🧪 테스트 방법

### 1. S3 연결 테스트
```python
# test_s3.py
import boto3

s3_client = boto3.client('s3', region_name='ap-northeast-2')

# 버킷 목록 확인
response = s3_client.list_buckets()
print("Buckets:", [b['Name'] for b in response['Buckets']])

# 테스트 파일 다운로드
response = s3_client.get_object(
    Bucket='story-game-bucket',
    Key='uploads/test.txt'
)
content = response['Body'].read().decode('utf-8')
print("Content:", content[:100])
```

### 2. Pre-signed URL 업로드 테스트
```python
# test_presigned_upload.py
import requests
import json

# 가정: Backend로부터 받은 Pre-signed URL
presigned_url = "https://story-game-bucket.s3.ap-northeast-2.amazonaws.com/stories/xyz.json?..."

# 테스트 데이터
test_story = {
    "metadata": {"title": "Test Story"},
    "episodes": []
}

# PUT 요청으로 업로드
response = requests.put(
    presigned_url,
    data=json.dumps(test_story, ensure_ascii=False).encode('utf-8'),
    headers={'Content-Type': 'application/json'}
)

print(f"Upload status: {response.status_code}")
```

### 3. 엔드포인트 테스트
```bash
# 분석 테스트
curl -X POST http://localhost:8000/analyze-from-s3 \
  -H "Content-Type: application/json" \
  -d '{
    "file_key": "uploads/test_novel.txt",
    "bucket": "story-game-bucket"
  }'

# 생성 테스트 (S3 방식)
curl -X POST http://localhost:8000/generate-from-s3 \
  -H "Content-Type: application/json" \
  -d '{
    "file_key": "uploads/test_novel.txt",
    "bucket": "story-game-bucket",
    "selected_gauge_ids": ["civilization", "unity"],
    "num_episodes": 3,
    "max_depth": 3,
    "ending_config": {"happy": 2, "tragic": 1},
    "num_episode_endings": 3
  }'

# 생성 테스트 (Pre-signed URL 방식)
curl -X POST http://localhost:8000/generate \
  -H "Content-Type: application/json" \
  -d '{
    "novel_text": "테스트 소설 내용...",
    "selected_gauge_ids": ["civilization", "unity"],
    "num_episodes": 3,
    "max_depth": 3,
    "ending_config": {"happy": 2, "tragic": 1},
    "num_episode_endings": 3,
    "file_key": "stories/test123.json",
    "s3_upload_url": "https://..."
  }'
```

---

## 📝 요약

### AI 서버가 구현해야 할 것

| 엔드포인트 | 기능 | S3 역할 | 상태 |
|-----------|------|---------|------|
| `/analyze-from-s3` | S3에서 소설 다운로드 → 분석 | AI 서버가 **읽기** | 필수 |
| `/generate-from-s3` | S3에서 소설 다운로드 → 생성 → **S3에 직접 업로드** ⭐ | AI 서버가 **읽기 + 쓰기** | **권장** |
| `/generate` (수정) | 스토리 생성 → Pre-signed URL로 업로드 | AI 서버가 **쓰기** | 선택 |

**핵심 변경사항**:
- ✅ `/generate-from-s3`에 `s3_upload_url`과 `s3_file_key` 파라미터 추가
- ✅ AI 서버가 생성 완료 후 Pre-signed URL로 S3에 직접 업로드
- ✅ Response에서 전체 JSON 대신 `file_key`와 `metadata`만 반환

### Backend는 준비 완료 ✅

- ✅ Pre-signed URL 생성 로직 (결과 업로드용)
- ✅ `StoryGenerationRequestDto`에 `s3_upload_url`, `s3_file_key` 필드 추가
- ✅ `StoryGenerationResponseDto`에 `file_key`, `metadata` 필드 추가
- ✅ 새 방식과 레거시 방식 모두 지원 (하위 호환성)
- ✅ AWS Credentials 설정

**다음 단계**: AI 서버 레포지토리에서 위 엔드포인트들을 구현하면 됩니다!

**예상 개선 효과**:
- 🚀 네트워크 트래픽 50% 감소 (큰 JSON이 Backend를 거치지 않음)
- 💾 Backend 메모리 사용량 대폭 감소
- ⚡ 전체 처리 시간 단축
