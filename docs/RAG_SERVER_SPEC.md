# RAG Server API Specification

Python 기반 RAG (Retrieval-Augmented Generation) 서버로, 게임 플레이 중 스토리 캐릭터와의 챗봇 대화를 제공합니다.

## 개요

### 역할
- 스토리의 캐릭터 정보를 벡터 DB에 인덱싱
- 사용자의 메시지를 받아 캐릭터 정보를 검색
- LLM과 통합하여 캐릭터에 맞는 일관된 응답 생성

### 기술 스택
- **Framework**: FastAPI
- **LLM**: OpenAI GPT-4 / Anthropic Claude
- **Vector DB**: PostgreSQL + pgvector 확장
- **Embeddings**: OpenAI Embeddings / SentenceTransformers
- **RAG Framework**: LangChain
- **DB Driver**: psycopg2 / asyncpg

---

## Architecture

```
┌──────────────────────────────────────────────────┐
│           Relay Server (8081)                    │
│  POST /ai/chat/index-character                   │
│  POST /ai/chat/message                           │
└────────────────┬─────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────┐
│         RAG Server (8002) - Python               │
│  ┌────────────────────────────────────────────┐  │
│  │ FastAPI Application                        │  │
│  │  POST /chat/index-character                │  │
│  │  POST /chat/message                        │  │
│  │  GET  /chat/health                         │  │
│  └────────┬────────────────────┬────────────┘  │
│           │                    │                 │
│           ▼                    ▼                 │
│  ┌─────────────────┐  ┌──────────────────────┐ │
│  │  LangChain      │  │  PostgreSQL +        │ │
│  │  - RAG Chain    │  │  pgvector            │ │
│  │  - LLM (GPT-4)  │  │  - Vector 검색       │ │
│  └─────────────────┘  │  - 임베딩 저장       │ │
│                       └──────────────────────┘ │
└──────────────────────────────────────────────────┘
```

---

## API Endpoints

### 1. POST /chat/index-character

캐릭터 정보를 벡터 DB에 인덱싱

**Request Body**:
```json
{
  "characterId": "char_001",
  "name": "간달프",
  "description": "회색의 마법사. 수천 년을 살아온 지혜로운 존재.",
  "personality": "지혜롭고, 신중하며, 때로는 엄격하지만 따뜻한 마음을 가짐",
  "background": "발리노르에서 온 마이아. 중간계를 지키는 사명을 받음.",
  "dialogueSamples": [
    "현명한 자는 오직 필요한 것만 말하지.",
    "어둠은 언제나 빛보다 먼저 오는 법이야.",
    "용기는 가장 작은 자에게서도 발견될 수 있단다."
  ],
  "relationships": {
    "프로도": "제자이자 친구. 깊은 신뢰 관계",
    "사우론": "오랜 적. 중간계의 위협"
  },
  "additionalInfo": {
    "appearance": "회색 로브, 긴 은색 수염, 지팡이",
    "abilities": ["마법", "검술", "지혜"],
    "age": "수천 년"
  }
}
```

**Response**:
```json
{
  "success": true,
  "characterId": "char_001",
  "indexed_documents": 15,
  "message": "Character indexed successfully"
}
```

**처리 로직**:
1. 캐릭터 정보를 텍스트로 분할
   - Description, personality, background를 각각 문서화
   - Dialogue samples를 개별 문서로 저장
   - Relationships를 문서화
2. 각 문서를 임베딩하여 Vector DB에 저장
3. Metadata에 characterId, sourceType 등 저장

---

### 2. POST /chat/message

캐릭터 챗봇에 메시지 전송

**Request Body**:
```json
{
  "characterId": "char_001",
  "userMessage": "당신은 누구인가요?",
  "conversationHistory": [
    {
      "role": "user",
      "content": "안녕하세요"
    },
    {
      "role": "assistant",
      "content": "반갑네. 무엇이 궁금한가?"
    }
  ],
  "maxTokens": 200
}
```

**Response**:
```json
{
  "characterId": "char_001",
  "aiMessage": "나는 간달프, 회색의 마법사라네. 수천 년을 살아오며 이 중간계를 지켜왔지. 자네는 누구인가?",
  "sources": [
    {
      "text": "회색의 마법사. 수천 년을 살아온 지혜로운 존재.",
      "score": 0.92,
      "sourceType": "description"
    },
    {
      "text": "발리노르에서 온 마이아. 중간계를 지키는 사명을 받음.",
      "score": 0.87,
      "sourceType": "background"
    }
  ],
  "timestamp": "2025-12-02T10:30:00Z"
}
```

**처리 로직**:
1. User message를 임베딩
2. Vector DB에서 해당 캐릭터의 관련 정보 검색 (top 5)
3. 검색된 정보 + conversation history를 LLM에 전달
4. System prompt에 캐릭터 성격 반영
5. LLM 응답 생성

**System Prompt 예시**:
```
당신은 "{character.name}"입니다.

성격: {character.personality}

다음 정보를 기반으로 응답하세요:
{retrieved_context}

대화 기록:
{conversation_history}

사용자: {user_message}

캐릭터에 맞는 말투와 성격으로 응답하세요. 일관성을 유지하고, 제공된 정보에 기반하여 답변하세요.
```

---

### 3. GET /chat/health

헬스 체크

**Response**:
```json
{
  "status": "healthy",
  "vectordb": "connected",
  "llm": "available"
}
```

---

## Data Flow

### 게임 시작 시 (캐릭터 인덱싱)

```
1. Backend가 스토리 데이터 로드
2. 모든 캐릭터 정보 추출
3. Relay Server → RAG Server: POST /chat/index-character (각 캐릭터마다)
4. RAG Server가 Vector DB에 인덱싱
5. 인덱싱 완료
```

### 대화 중

```
1. Frontend: 사용자가 캐릭터 클릭 → 대화창 열림
2. Frontend → Backend: POST /api/game/{sessionId}/chat/start
3. Frontend: 사용자 메시지 입력 "당신은 누구인가요?"
4. Frontend → Backend: POST /api/game/{sessionId}/chat/message
5. Backend → Relay: POST /ai/chat/message
6. Relay → RAG Server: POST /chat/message
7. RAG Server:
   - User message 임베딩
   - Vector DB에서 캐릭터 정보 검색
   - LLM에 컨텍스트 + 메시지 전달
   - AI 응답 생성
8. RAG Server → Relay → Backend → Frontend
9. Frontend: AI 응답 표시 "나는 간달프, 회색의 마법사라네..."
```

---

## Python 샘플 코드

### Directory Structure

```
rag-server/
├── main.py                  # FastAPI 애플리케이션
├── models/
│   ├── request_models.py    # Request DTOs
│   └── response_models.py   # Response DTOs
├── services/
│   ├── vectordb_service.py  # Vector DB 관리
│   ├── embedding_service.py # 임베딩 생성
│   └── chat_service.py      # RAG 챗봇 로직
├── config.py                # 설정
├── requirements.txt
└── .env
```

### main.py

```python
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Dict, Optional
import uvicorn

from services.chat_service import ChatService
from models.request_models import CharacterIndexRequest, ChatMessageRequest
from models.response_models import ChatMessageResponse, IndexResponse

app = FastAPI(title="RAG Server - Character Chatbot")

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Services
chat_service = ChatService()

@app.post("/chat/index-character", response_model=IndexResponse)
async def index_character(request: CharacterIndexRequest):
    """캐릭터 정보를 Vector DB에 인덱싱"""
    try:
        result = chat_service.index_character(request)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/chat/message", response_model=ChatMessageResponse)
async def send_message(request: ChatMessageRequest):
    """캐릭터와 대화"""
    try:
        response = chat_service.generate_response(request)
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/chat/health")
async def health_check():
    """헬스 체크"""
    return {
        "status": "healthy",
        "vectordb": "connected",
        "llm": "available"
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8002)
```

### services/chat_service.py

```python
from langchain_postgres import PGVector
from langchain.embeddings import OpenAIEmbeddings
from langchain.chat_models import ChatOpenAI
from langchain.prompts import ChatPromptTemplate
from datetime import datetime
import os

class ChatService:
    def __init__(self):
        self.embeddings = OpenAIEmbeddings(
            openai_api_key=os.getenv("OPENAI_API_KEY")
        )

        # PostgreSQL + pgvector 연결
        connection_string = (
            f"postgresql+psycopg2://{os.getenv('DB_USER')}:"
            f"{os.getenv('DB_PASSWORD')}@{os.getenv('DB_HOST')}:"
            f"{os.getenv('DB_PORT')}/{os.getenv('DB_NAME')}"
        )

        self.vectorstore = PGVector(
            connection_string=connection_string,
            embedding_function=self.embeddings,
            collection_name="character_embeddings",
            use_jsonb=True
        )

        self.llm = ChatOpenAI(
            model="gpt-4",
            temperature=0.7,
            openai_api_key=os.getenv("OPENAI_API_KEY")
        )

    def index_character(self, request):
        """캐릭터 정보 인덱싱"""
        documents = []
        metadatas = []

        # Description
        documents.append(request.description)
        metadatas.append({
            "character_id": request.characterId,
            "source_type": "description"
        })

        # Personality
        documents.append(f"성격: {request.personality}")
        metadatas.append({
            "character_id": request.characterId,
            "source_type": "personality"
        })

        # Background
        documents.append(f"배경: {request.background}")
        metadatas.append({
            "character_id": request.characterId,
            "source_type": "background"
        })

        # Dialogue samples
        for dialogue in request.dialogueSamples:
            documents.append(f"{request.name}의 대사: {dialogue}")
            metadatas.append({
                "character_id": request.characterId,
                "source_type": "dialogue"
            })

        # Add to vector store
        self.vectorstore.add_texts(
            texts=documents,
            metadatas=metadatas
        )

        return {
            "success": True,
            "characterId": request.characterId,
            "indexed_documents": len(documents),
            "message": "Character indexed successfully"
        }

    def generate_response(self, request):
        """RAG 기반 응답 생성"""
        # 1. 벡터 검색
        results = self.vectorstore.similarity_search_with_score(
            query=request.userMessage,
            k=5,
            filter={"character_id": request.characterId}
        )

        # 2. 컨텍스트 구성
        context = "\n\n".join([doc.page_content for doc, score in results])

        # 3. Conversation history 구성
        history = ""
        for msg in request.conversationHistory or []:
            role = "사용자" if msg["role"] == "user" else "캐릭터"
            history += f"{role}: {msg['content']}\n"

        # 4. Prompt 생성
        prompt = ChatPromptTemplate.from_template("""
당신은 게임 속 캐릭터입니다.

다음 정보를 기반으로 일관된 캐릭터로 응답하세요:
{context}

대화 기록:
{history}

사용자의 메시지: {user_message}

캐릭터의 성격과 말투를 유지하며 자연스럽게 응답하세요.
""")

        # 5. LLM 호출
        chain = prompt | self.llm
        ai_response = chain.invoke({
            "context": context,
            "history": history,
            "user_message": request.userMessage
        })

        # 6. 응답 구성
        sources = [
            {
                "text": doc.page_content,
                "score": float(score),
                "sourceType": doc.metadata.get("source_type", "unknown")
            }
            for doc, score in results[:3]  # Top 3 sources
        ]

        return {
            "characterId": request.characterId,
            "aiMessage": ai_response.content,
            "sources": sources,
            "timestamp": datetime.now().isoformat()
        }
```

### requirements.txt

```
fastapi==0.104.1
uvicorn==0.24.0
langchain==0.1.0
langchain-postgres==0.0.3
openai==1.3.0
psycopg2-binary==2.9.9
sqlalchemy==2.0.23
pgvector==0.2.4
pydantic==2.5.0
python-dotenv==1.0.0
```

### .env

```
# OpenAI / Claude API
OPENAI_API_KEY=your-openai-api-key
ANTHROPIC_API_KEY=your-claude-api-key  # Claude 사용 시

# PostgreSQL 연결 정보
DB_HOST=localhost
DB_PORT=5432
DB_NAME=story_game_rag
DB_USER=postgres
DB_PASSWORD=your-password
```

---

## PostgreSQL + pgvector 설정

### 1. pgvector 확장 설치

**Ubuntu/Debian**:
```bash
sudo apt update
sudo apt install postgresql-15-pgvector
```

**macOS (Homebrew)**:
```bash
brew install pgvector
```

**Windows**:
pgvector 바이너리를 다운로드하거나 Docker 사용 권장

**Docker (권장)**:
```bash
docker run -d \
  --name rag-postgres \
  -e POSTGRES_PASSWORD=your-password \
  -e POSTGRES_DB=story_game_rag \
  -p 5432:5432 \
  ankane/pgvector
```

### 2. 데이터베이스 생성 및 확장 활성화

```sql
-- 데이터베이스 생성
CREATE DATABASE story_game_rag;

-- 데이터베이스 연결
\c story_game_rag

-- pgvector 확장 활성화
CREATE EXTENSION IF NOT EXISTS vector;

-- 확인
SELECT * FROM pg_extension WHERE extname = 'vector';
```

### 3. 테이블 스키마

LangChain의 PGVector가 자동으로 테이블을 생성하지만, 수동으로 생성하려면:

```sql
-- 임베딩 컬렉션 테이블
CREATE TABLE langchain_pg_collection (
    name VARCHAR PRIMARY KEY,
    cmetadata JSON
);

-- 임베딩 저장 테이블
CREATE TABLE langchain_pg_embedding (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    collection_id VARCHAR REFERENCES langchain_pg_collection(name) ON DELETE CASCADE,
    embedding VECTOR(1536),  -- OpenAI ada-002 임베딩 차원
    document TEXT,
    cmetadata JSONB
);

-- 벡터 검색 성능을 위한 인덱스 (HNSW 알고리즘)
CREATE INDEX ON langchain_pg_embedding
USING hnsw (embedding vector_cosine_ops);

-- 또는 IVFFlat 인덱스 (더 빠른 인덱싱, 약간 느린 검색)
-- CREATE INDEX ON langchain_pg_embedding
-- USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- 메타데이터 검색을 위한 인덱스
CREATE INDEX ON langchain_pg_embedding USING GIN (cmetadata);
```

### 4. 벡터 인덱스 선택

**HNSW (Hierarchical Navigable Small World)**:
- 장점: 빠른 검색 속도, 높은 정확도
- 단점: 인덱스 빌드 시간 오래 걸림
- 추천: 데이터가 적을 때 (< 100만 벡터)

**IVFFlat (Inverted File with Flat compression)**:
- 장점: 빠른 인덱스 빌드
- 단점: 검색 속도 약간 느림
- 추천: 데이터가 많을 때 (> 100만 벡터)

### 5. 연결 테스트

```python
import psycopg2
from pgvector.psycopg2 import register_vector

# 연결
conn = psycopg2.connect(
    host="localhost",
    port=5432,
    database="story_game_rag",
    user="postgres",
    password="your-password"
)

# pgvector 등록
register_vector(conn)

# 테스트 쿼리
cur = conn.cursor()
cur.execute("SELECT 1")
print("Connection successful!")

cur.close()
conn.close()
```

---

## 환경 변수 설정 (Relay Server)

Relay Server에서 RAG 서버 URL을 설정:

```bash
export AI_RAG_URL=http://localhost:8002
```

---

## 테스트 시나리오

### 1. 캐릭터 인덱싱 테스트

```bash
curl -X POST http://localhost:8002/chat/index-character \
  -H "Content-Type: application/json" \
  -d '{
    "characterId": "char_001",
    "name": "간달프",
    "description": "회색의 마법사",
    "personality": "지혜롭고 신중함",
    "background": "발리노르에서 온 마이아",
    "dialogueSamples": ["현명한 자는 오직 필요한 것만 말하지."],
    "relationships": {},
    "additionalInfo": {}
  }'
```

### 2. 대화 테스트

```bash
curl -X POST http://localhost:8002/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "characterId": "char_001",
    "userMessage": "당신은 누구인가요?",
    "conversationHistory": [],
    "maxTokens": 200
  }'
```

---

## PostgreSQL 쿼리 예제

### 벡터 유사도 검색

```sql
-- 코사인 유사도로 가장 유사한 5개 찾기
SELECT
    document,
    cmetadata,
    1 - (embedding <=> '[0.1, 0.2, ..., 0.9]'::vector) as similarity
FROM langchain_pg_embedding
WHERE cmetadata->>'character_id' = 'char_001'
ORDER BY embedding <=> '[0.1, 0.2, ..., 0.9]'::vector
LIMIT 5;

-- L2 거리 사용
SELECT
    document,
    embedding <-> '[0.1, 0.2, ..., 0.9]'::vector as distance
FROM langchain_pg_embedding
ORDER BY embedding <-> '[0.1, 0.2, ..., 0.9]'::vector
LIMIT 5;

-- Inner product 사용
SELECT
    document,
    (embedding <#> '[0.1, 0.2, ..., 0.9]'::vector) * -1 as inner_product
FROM langchain_pg_embedding
ORDER BY embedding <#> '[0.1, 0.2, ..., 0.9]'::vector
LIMIT 5;
```

### 인덱스 성능 확인

```sql
-- 인덱스 사용 여부 확인
EXPLAIN ANALYZE
SELECT document
FROM langchain_pg_embedding
ORDER BY embedding <=> '[0.1, 0.2, ..., 0.9]'::vector
LIMIT 5;

-- 인덱스 크기 확인
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) as index_size
FROM pg_stat_user_indexes
WHERE tablename = 'langchain_pg_embedding';
```

---

## Docker Compose 설정 (권장)

RAG Server + PostgreSQL을 함께 실행:

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: ankane/pgvector:latest
    container_name: rag-postgres
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: your-password
      POSTGRES_DB: story_game_rag
    ports:
      - "5432:5432"
    volumes:
      - pgvector_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  rag-server:
    build: .
    container_name: rag-server
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - DB_HOST=postgres
      - DB_PORT=5432
      - DB_NAME=story_game_rag
      - DB_USER=postgres
      - DB_PASSWORD=your-password
    ports:
      - "8002:8002"
    depends_on:
      postgres:
        condition: service_healthy
    volumes:
      - ./:/app

volumes:
  pgvector_data:
```

실행:
```bash
docker-compose up -d
```

---

## 성능 최적화 팁

### 1. 임베딩 캐싱
자주 검색되는 쿼리는 캐싱:

```python
from functools import lru_cache

@lru_cache(maxsize=1000)
def get_embedding(text: str):
    return embeddings.embed_query(text)
```

### 2. 배치 인덱싱
여러 캐릭터를 한 번에 인덱싱:

```python
def index_multiple_characters(characters: List[Character]):
    all_documents = []
    all_metadatas = []

    for character in characters:
        docs, metas = prepare_character_documents(character)
        all_documents.extend(docs)
        all_metadatas.extend(metas)

    # 한 번에 인덱싱
    vectorstore.add_texts(
        texts=all_documents,
        metadatas=all_metadatas
    )
```

### 3. 연결 풀 사용

```python
from sqlalchemy import create_engine
from sqlalchemy.pool import QueuePool

engine = create_engine(
    connection_string,
    poolclass=QueuePool,
    pool_size=10,
    max_overflow=20
)
```

### 4. 비동기 처리

```python
from langchain_postgres.vectorstores import PGVector
import asyncpg

# 비동기 연결
async def get_similar_documents(query: str):
    async with asyncpg.create_pool(connection_string) as pool:
        # 비동기 검색
        results = await vectorstore.asimilarity_search(query, k=5)
        return results
```

---

## 모니터링

### PostgreSQL 쿼리 성능

```sql
-- 느린 쿼리 확인
SELECT
    query,
    calls,
    total_time,
    mean_time,
    max_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;

-- 인덱스 사용률
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE tablename = 'langchain_pg_embedding';
```

### 벡터 검색 품질 평가

```python
def evaluate_search_quality(test_queries):
    """검색 품질 평가"""
    results = []

    for query, expected_docs in test_queries:
        retrieved = vectorstore.similarity_search(query, k=5)

        # Precision@5
        relevant = [doc for doc in retrieved if doc.page_content in expected_docs]
        precision = len(relevant) / 5

        results.append({
            "query": query,
            "precision": precision,
            "retrieved": [doc.page_content for doc in retrieved]
        })

    return results
```

---

## Next Steps

1. ✅ Backend & Relay Server에 Chat API 추가 완료
2. ✅ PostgreSQL + pgvector 설정 가이드 작성
3. ⏳ Python RAG Server 개발 (별도 레포지토리)
4. ⏳ PostgreSQL + pgvector 설치 및 설정
5. ⏳ LLM API 키 설정 (OpenAI/Anthropic)
6. ⏳ 통합 테스트

RAG 서버를 별도 레포지토리로 개발하시면 됩니다!

---

## 추가 참고 자료

- **pgvector GitHub**: https://github.com/pgvector/pgvector
- **LangChain PGVector**: https://python.langchain.com/docs/integrations/vectorstores/pgvector
- **pgvector 성능 가이드**: https://github.com/pgvector/pgvector#performance
