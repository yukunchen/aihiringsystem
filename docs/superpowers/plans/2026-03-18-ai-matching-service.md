# AI Matching Service Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Python/FastAPI service that vectorizes resumes/JDs via litellm embeddings, stores vectors in Qdrant, scores matches via LLM, and integrate it into the Spring Boot monolith via events and a new `/api/match` endpoint.

**Architecture:** New `ai-matching-service/` Python service handles all AI operations (embed, store, search, score). Spring Boot publishes events on resume upload and JD save/update; a new `@Async` event listener calls the AI service. A new Spring `MatchController` proxies match requests. All inter-service traffic is Docker-internal; only Nginx is exposed publicly.

**Tech Stack:** Python 3.12, FastAPI, litellm, qdrant-client, pydantic-settings, pytest / Java 21, Spring Boot 3.3, RestClient, WireMock, JUnit 5 + Mockito

---

## Chunk 1: Python Service Foundation

### Task 1: Project Scaffold

**Files:**
- Create: `ai-matching-service/requirements.txt`
- Create: `ai-matching-service/Dockerfile`
- Create: `ai-matching-service/routers/__init__.py`
- Create: `ai-matching-service/services/__init__.py`
- Create: `ai-matching-service/tests/__init__.py`

- [ ] **Step 1: Create directory structure**

```bash
mkdir -p ai-matching-service/routers \
         ai-matching-service/services \
         ai-matching-service/tests
touch ai-matching-service/routers/__init__.py \
      ai-matching-service/services/__init__.py \
      ai-matching-service/tests/__init__.py
```

- [ ] **Step 2: Write `requirements.txt`**

```
fastapi==0.115.0
uvicorn[standard]==0.30.6
litellm==1.44.0
qdrant-client==1.11.0
pydantic-settings==2.5.2
httpx==0.27.2
pytest==8.3.3
pytest-asyncio==0.24.0
```

- [ ] **Step 3: Write `Dockerfile`**

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
```

- [ ] **Step 4: Commit scaffold**

```bash
git add ai-matching-service/
git commit -m "feat: scaffold ai-matching-service directory structure"
```

---

### Task 2: Config and Schemas

**Files:**
- Create: `ai-matching-service/config.py`
- Create: `ai-matching-service/schemas.py`

- [ ] **Step 1: Write `config.py`**

```python
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    llm_model: str = "gpt-4o-mini"
    embedding_model: str = "text-embedding-3-small"
    vector_size: int = 1536
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333
    port: int = 8001
    log_level: str = "info"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")


settings = Settings()
```

- [ ] **Step 2: Write `schemas.py`**

```python
from pydantic import BaseModel, Field
from typing import Optional


class VectorizeResumeRequest(BaseModel):
    resume_id: str
    raw_text: Optional[str] = None


class VectorizeJobRequest(BaseModel):
    job_id: str
    title: str
    description: str
    requirements: Optional[str] = None
    skills: Optional[str] = None


class VectorizeResponse(BaseModel):
    status: str
    reason: Optional[str] = None


class MatchRequest(BaseModel):
    job_id: str
    top_k: int = Field(default=10, ge=1, le=50)


class MatchResultItem(BaseModel):
    resume_id: str
    vector_score: float
    llm_score: int
    reasoning: str
    highlights: list[str]


class MatchResponse(BaseModel):
    job_id: str
    results: list[MatchResultItem]


class MatchScore(BaseModel):
    score: int
    reasoning: str
    highlights: list[str]


class HealthResponse(BaseModel):
    status: str
    qdrant: str
    llm_model: str
    embedding_model: str
```

- [ ] **Step 3: Commit**

```bash
git add ai-matching-service/config.py ai-matching-service/schemas.py
git commit -m "feat: add config and schemas for ai-matching-service"
```

---

### Task 3: Embedding Service (TDD)

**Files:**
- Create: `ai-matching-service/services/embedding.py`

- [ ] **Step 1: Write the failing test**

Create `ai-matching-service/tests/test_embedding.py`:

```python
import pytest
from unittest.mock import AsyncMock, patch, MagicMock


@pytest.mark.asyncio
async def test_embed_text_returns_vector():
    mock_response = MagicMock()
    mock_response.data = [{"embedding": [0.1, 0.2, 0.3]}]

    with patch("litellm.aembedding", new=AsyncMock(return_value=mock_response)) as mock_embed:
        from services.embedding import embed_text
        result = await embed_text("hello world")

    assert result == [0.1, 0.2, 0.3]
    mock_embed.assert_called_once()
    call_kwargs = mock_embed.call_args
    assert call_kwargs.kwargs["input"] == ["hello world"]


@pytest.mark.asyncio
async def test_embed_text_uses_configured_model():
    mock_response = MagicMock()
    mock_response.data = [{"embedding": [0.5]}]

    with patch("litellm.aembedding", new=AsyncMock(return_value=mock_response)) as mock_embed:
        from services.embedding import embed_text
        await embed_text("test")

    assert mock_embed.call_args.kwargs["model"] == "text-embedding-3-small"
```

- [ ] **Step 2: Run to confirm it fails**

```bash
cd ai-matching-service
python -m pytest tests/test_embedding.py -v
```
Expected: `ModuleNotFoundError: No module named 'services.embedding'`

- [ ] **Step 3: Implement `services/embedding.py`**

```python
import litellm
from config import settings


async def embed_text(text: str) -> list[float]:
    response = await litellm.aembedding(
        model=settings.embedding_model,
        input=[text]
    )
    return response.data[0]["embedding"]
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
python -m pytest tests/test_embedding.py -v
```
Expected: 2 PASSED

- [ ] **Step 5: Commit**

```bash
git add services/embedding.py tests/test_embedding.py
git commit -m "feat: add embedding service with litellm abstraction"
```

---

### Task 4: Vector Store Service (TDD)

**Files:**
- Create: `ai-matching-service/services/vector_store.py`

- [ ] **Step 1: Write the failing tests**

Create `ai-matching-service/tests/test_vector_store.py`:

```python
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
import services.vector_store as vs


@pytest.fixture(autouse=True)
def mock_client():
    mock = AsyncMock()
    mock.get_collections = AsyncMock(return_value=MagicMock(collections=[]))
    mock.create_collection = AsyncMock()
    vs.client = mock
    return mock


@pytest.mark.asyncio
async def test_ensure_collections_creates_missing():
    mock = vs.client
    mock.get_collections.return_value = MagicMock(collections=[])

    await vs.ensure_collections()

    assert mock.create_collection.call_count == 2
    names_created = {
        call.kwargs["collection_name"]
        for call in mock.create_collection.call_args_list
    }
    assert names_created == {"resumes", "jobs"}


@pytest.mark.asyncio
async def test_ensure_collections_skips_existing():
    existing_col = MagicMock()
    existing_col.name = "resumes"
    vs.client.get_collections.return_value = MagicMock(collections=[existing_col])

    await vs.ensure_collections()

    # Only "jobs" should be created
    assert vs.client.create_collection.call_count == 1
    assert vs.client.create_collection.call_args.kwargs["collection_name"] == "jobs"


@pytest.mark.asyncio
async def test_upsert_resume_stores_vector_and_text():
    await vs.upsert_resume("resume-123", [0.1, 0.2], "John Smith resume text")

    vs.client.upsert.assert_called_once()
    call_kwargs = vs.client.upsert.call_args.kwargs
    assert call_kwargs["collection_name"] == "resumes"
    point = call_kwargs["points"][0]
    assert str(point.id) == "resume-123"
    assert point.vector == [0.1, 0.2]
    assert point.payload["resume_id"] == "resume-123"
    assert point.payload["raw_text"] == "John Smith resume text"


@pytest.mark.asyncio
async def test_upsert_job_stores_vector_and_payload():
    await vs.upsert_job("job-456", [0.3, 0.4], "Engineer", "Build things", "5yr exp", "Java")

    vs.client.upsert.assert_called_once()
    call_kwargs = vs.client.upsert.call_args.kwargs
    assert call_kwargs["collection_name"] == "jobs"
    point = call_kwargs["points"][0]
    assert point.payload["job_id"] == "job-456"
    assert point.payload["title"] == "Engineer"


@pytest.mark.asyncio
async def test_get_job_vector_returns_vector_and_payload():
    mock_record = MagicMock()
    mock_record.vector = [0.5, 0.6]
    mock_record.payload = {"job_id": "job-456", "title": "Engineer", "description": "Build things"}
    vs.client.retrieve = AsyncMock(return_value=[mock_record])

    result = await vs.get_job_record("job-456")

    assert result is not None
    assert result.vector == [0.5, 0.6]
    vs.client.retrieve.assert_called_once_with(
        collection_name="jobs", ids=["job-456"], with_vectors=True
    )


@pytest.mark.asyncio
async def test_get_job_vector_returns_none_when_not_found():
    vs.client.retrieve = AsyncMock(return_value=[])

    result = await vs.get_job_record("missing-job")

    assert result is None


@pytest.mark.asyncio
async def test_search_resumes_returns_scored_points():
    mock_point = MagicMock()
    mock_point.id = "resume-123"
    mock_point.score = 0.92
    mock_point.payload = {"resume_id": "resume-123", "raw_text": "some text"}
    vs.client.search = AsyncMock(return_value=[mock_point])

    results = await vs.search_resumes([0.1, 0.2], top_k=5)

    assert len(results) == 1
    assert results[0].score == 0.92
    vs.client.search.assert_called_once_with(
        collection_name="resumes", query_vector=[0.1, 0.2], limit=5
    )
```

- [ ] **Step 2: Run to confirm they fail**

```bash
python -m pytest tests/test_vector_store.py -v
```
Expected: `ModuleNotFoundError: No module named 'services.vector_store'`

- [ ] **Step 3: Implement `services/vector_store.py`**

```python
from qdrant_client import AsyncQdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct
from config import settings

client = AsyncQdrantClient(host=settings.qdrant_host, port=settings.qdrant_port)


async def ensure_collections():
    existing = await client.get_collections()
    existing_names = {c.name for c in existing.collections}

    if "resumes" not in existing_names:
        await client.create_collection(
            collection_name="resumes",
            vectors_config=VectorParams(size=settings.vector_size, distance=Distance.COSINE),
        )

    if "jobs" not in existing_names:
        await client.create_collection(
            collection_name="jobs",
            vectors_config=VectorParams(size=settings.vector_size, distance=Distance.COSINE),
        )


async def upsert_resume(resume_id: str, vector: list[float], raw_text: str):
    await client.upsert(
        collection_name="resumes",
        points=[PointStruct(
            id=resume_id,
            vector=vector,
            payload={"resume_id": resume_id, "raw_text": raw_text},
        )],
    )


async def upsert_job(
    job_id: str, vector: list[float], title: str,
    description: str, requirements: str | None, skills: str | None
):
    await client.upsert(
        collection_name="jobs",
        points=[PointStruct(
            id=job_id,
            vector=vector,
            payload={
                "job_id": job_id,
                "title": title,
                "description": description,
                "requirements": requirements,
                "skills": skills,
            },
        )],
    )


async def get_job_record(job_id: str):
    results = await client.retrieve(
        collection_name="jobs", ids=[job_id], with_vectors=True
    )
    return results[0] if results else None


async def search_resumes(job_vector: list[float], top_k: int):
    return await client.search(
        collection_name="resumes", query_vector=job_vector, limit=top_k
    )
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
python -m pytest tests/test_vector_store.py -v
```
Expected: 7 PASSED

- [ ] **Step 5: Commit**

```bash
git add services/vector_store.py tests/test_vector_store.py
git commit -m "feat: add vector store service with Qdrant operations"
```

---

### Task 5: LLM Scoring Service (TDD)

**Files:**
- Create: `ai-matching-service/services/llm.py`

- [ ] **Step 1: Write the failing tests**

Create `ai-matching-service/tests/test_llm.py`:

```python
import pytest
import json
from unittest.mock import AsyncMock, MagicMock, patch


@pytest.mark.asyncio
async def test_score_match_returns_match_score():
    mock_content = json.dumps({"score": 85, "reasoning": "Strong Java background", "highlights": ["Java", "Spring"]})
    mock_response = MagicMock()
    mock_response.choices[0].message.content = mock_content

    with patch("litellm.acompletion", new=AsyncMock(return_value=mock_response)):
        from services.llm import score_match
        result = await score_match("Java developer role", "John Smith Java engineer")

    assert result.score == 85
    assert result.reasoning == "Strong Java background"
    assert result.highlights == ["Java", "Spring"]


@pytest.mark.asyncio
async def test_score_match_uses_configured_model():
    mock_content = json.dumps({"score": 70, "reasoning": "OK match", "highlights": []})
    mock_response = MagicMock()
    mock_response.choices[0].message.content = mock_content

    with patch("litellm.acompletion", new=AsyncMock(return_value=mock_response)) as mock_call:
        from services.llm import score_match
        await score_match("job text", "resume text")

    call_kwargs = mock_call.call_args.kwargs
    assert call_kwargs["model"] == "gpt-4o-mini"
    assert call_kwargs["temperature"] == 0.1
    assert call_kwargs["response_format"] == {"type": "json_object"}


@pytest.mark.asyncio
async def test_score_match_sends_job_and_resume_in_prompt():
    mock_content = json.dumps({"score": 50, "reasoning": "Partial", "highlights": []})
    mock_response = MagicMock()
    mock_response.choices[0].message.content = mock_content

    with patch("litellm.acompletion", new=AsyncMock(return_value=mock_response)) as mock_call:
        from services.llm import score_match
        await score_match("Senior Engineer role", "Alice Python developer")

    messages = mock_call.call_args.kwargs["messages"]
    user_message = next(m for m in messages if m["role"] == "user")
    assert "Senior Engineer role" in user_message["content"]
    assert "Alice Python developer" in user_message["content"]
```

- [ ] **Step 2: Run to confirm they fail**

```bash
python -m pytest tests/test_llm.py -v
```
Expected: `ModuleNotFoundError: No module named 'services.llm'`

- [ ] **Step 3: Implement `services/llm.py`**

```python
import litellm
from config import settings
from schemas import MatchScore

SCORING_SYSTEM_PROMPT = """You are a recruiting assistant. Score how well this resume matches the job description.

Return JSON only:
{
  "score": <integer 0-100>,
  "reasoning": "<2-3 sentences explaining the match quality>",
  "highlights": ["<key matching point>", ...]
}

Score guide: 80-100 = strong match, 60-79 = good match, 40-59 = partial match, 0-39 = weak match."""


async def score_match(job_text: str, resume_text: str) -> MatchScore:
    response = await litellm.acompletion(
        model=settings.llm_model,
        messages=[
            {"role": "system", "content": SCORING_SYSTEM_PROMPT},
            {"role": "user", "content": f"Job:\n{job_text}\n\nResume:\n{resume_text}"},
        ],
        response_format={"type": "json_object"},
        temperature=0.1,
    )
    return MatchScore.model_validate_json(response.choices[0].message.content)
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
python -m pytest tests/test_llm.py -v
```
Expected: 3 PASSED

- [ ] **Step 5: Commit**

```bash
git add services/llm.py tests/test_llm.py
git commit -m "feat: add LLM scoring service with litellm abstraction"
```

---

## Chunk 2: Python Routers and App Assembly

### Task 6: Test Fixtures (conftest)

**Files:**
- Create: `ai-matching-service/tests/conftest.py`

- [ ] **Step 1: Write `tests/conftest.py`**

```python
import pytest
from unittest.mock import AsyncMock, MagicMock
from fastapi.testclient import TestClient
import services.vector_store as vs


@pytest.fixture(autouse=True)
def mock_qdrant_client():
    """Replace the Qdrant client for every test to avoid real network calls."""
    mock = AsyncMock()
    mock.get_collections = AsyncMock(return_value=MagicMock(collections=[]))
    mock.create_collection = AsyncMock()
    original = vs.client
    vs.client = mock
    yield mock
    vs.client = original


@pytest.fixture(scope="module")
def client():
    from main import app
    with TestClient(app) as c:
        yield c
```

- [ ] **Step 2: Commit**

```bash
git add tests/conftest.py
git commit -m "feat: add test fixtures with Qdrant client mock"
```

---

### Task 7: Main App

**Files:**
- Create: `ai-matching-service/main.py`

- [ ] **Step 1: Write the failing test**

Create `ai-matching-service/tests/test_health.py`:

```python
def test_health_returns_ok(client):
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "ok"
    assert data["qdrant"] == "ok"
    assert "llm_model" in data
    assert "embedding_model" in data
```

- [ ] **Step 2: Run to confirm it fails**

```bash
python -m pytest tests/test_health.py -v
```
Expected: `ModuleNotFoundError: No module named 'main'`

- [ ] **Step 3: Implement `main.py`**

```python
import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from config import settings
from schemas import HealthResponse
import services.vector_store as vector_store
from routers import vectorize, match

logging.basicConfig(level=settings.log_level.upper())


@asynccontextmanager
async def lifespan(app: FastAPI):
    await vector_store.ensure_collections()
    yield


app = FastAPI(title="AI Matching Service", lifespan=lifespan)

app.include_router(vectorize.router, prefix="/internal/vectorize")
app.include_router(match.router)


@app.get("/health", response_model=HealthResponse)
async def health():
    try:
        await vector_store.client.get_collections()
        qdrant_status = "ok"
    except Exception:
        qdrant_status = "error"

    return HealthResponse(
        status="ok",
        qdrant=qdrant_status,
        llm_model=settings.llm_model,
        embedding_model=settings.embedding_model,
    )
```

- [ ] **Step 4: Run test to confirm it passes**

```bash
python -m pytest tests/test_health.py -v
```
Expected: 1 PASSED

- [ ] **Step 5: Commit**

```bash
git add main.py tests/test_health.py
git commit -m "feat: add FastAPI app with health endpoint and lifespan"
```

---

### Task 8: Vectorize Router (TDD)

**Files:**
- Create: `ai-matching-service/routers/vectorize.py`
- Create: `ai-matching-service/tests/test_vectorize.py`

- [ ] **Step 1: Write the failing tests**

Create `ai-matching-service/tests/test_vectorize.py`:

```python
import pytest
from unittest.mock import AsyncMock, patch


def test_vectorize_resume_with_text_returns_ok(client):
    with patch("services.embedding.embed_text", new=AsyncMock(return_value=[0.1] * 1536)), \
         patch("services.vector_store.upsert_resume", new=AsyncMock()):
        response = client.post("/internal/vectorize/resume", json={
            "resume_id": "550e8400-e29b-41d4-a716-446655440000",
            "raw_text": "John Smith Software Engineer",
        })
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_vectorize_resume_with_null_text_returns_skipped(client):
    response = client.post("/internal/vectorize/resume", json={
        "resume_id": "550e8400-e29b-41d4-a716-446655440000",
        "raw_text": None,
    })
    assert response.status_code == 200
    assert response.json()["status"] == "skipped"
    assert "reason" in response.json()


def test_vectorize_resume_calls_embed_and_upsert(client):
    embed_mock = AsyncMock(return_value=[0.5] * 1536)
    upsert_mock = AsyncMock()

    with patch("services.embedding.embed_text", new=embed_mock), \
         patch("services.vector_store.upsert_resume", new=upsert_mock):
        client.post("/internal/vectorize/resume", json={
            "resume_id": "550e8400-e29b-41d4-a716-446655440001",
            "raw_text": "Alice developer",
        })

    embed_mock.assert_called_once_with("Alice developer")
    upsert_mock.assert_called_once_with(
        "550e8400-e29b-41d4-a716-446655440001", [0.5] * 1536, "Alice developer"
    )


def test_vectorize_job_returns_ok(client):
    with patch("services.embedding.embed_text", new=AsyncMock(return_value=[0.1] * 1536)), \
         patch("services.vector_store.upsert_job", new=AsyncMock()):
        response = client.post("/internal/vectorize/job", json={
            "job_id": "660e8400-e29b-41d4-a716-446655440000",
            "title": "Senior Java Developer",
            "description": "Build great software",
            "requirements": "5 years Java",
            "skills": '["Java", "Spring Boot"]',
        })
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_vectorize_job_constructs_text_with_all_fields(client):
    embed_mock = AsyncMock(return_value=[0.1] * 1536)
    upsert_mock = AsyncMock()

    with patch("services.embedding.embed_text", new=embed_mock), \
         patch("services.vector_store.upsert_job", new=upsert_mock):
        client.post("/internal/vectorize/job", json={
            "job_id": "660e8400-e29b-41d4-a716-446655440001",
            "title": "Engineer",
            "description": "Core work",
            "requirements": "3yr exp",
            "skills": "Python",
        })

    embedded_text = embed_mock.call_args[0][0]
    assert "Engineer" in embedded_text
    assert "Core work" in embedded_text
    assert "3yr exp" in embedded_text
    assert "Python" in embedded_text


def test_vectorize_job_upsert_is_idempotent(client):
    """Re-vectorizing the same ID overwrites — upsert is called each time."""
    upsert_mock = AsyncMock()
    with patch("services.embedding.embed_text", new=AsyncMock(return_value=[0.1] * 1536)), \
         patch("services.vector_store.upsert_job", new=upsert_mock):
        for _ in range(2):
            client.post("/internal/vectorize/job", json={
                "job_id": "same-job-id",
                "title": "T", "description": "D",
            })

    assert upsert_mock.call_count == 2
```

- [ ] **Step 2: Run to confirm they fail**

```bash
python -m pytest tests/test_vectorize.py -v
```
Expected: failures because `routers/vectorize.py` doesn't exist yet

- [ ] **Step 3: Implement `routers/vectorize.py`**

```python
from fastapi import APIRouter
from schemas import VectorizeResumeRequest, VectorizeJobRequest, VectorizeResponse
from services import embedding, vector_store

router = APIRouter()


@router.post("/resume", response_model=VectorizeResponse)
async def vectorize_resume(request: VectorizeResumeRequest):
    if not request.raw_text:
        return VectorizeResponse(status="skipped", reason="no text")

    vector = await embedding.embed_text(request.raw_text)
    await vector_store.upsert_resume(request.resume_id, vector, request.raw_text)
    return VectorizeResponse(status="ok")


@router.post("/job", response_model=VectorizeResponse)
async def vectorize_job(request: VectorizeJobRequest):
    job_text = f"Title: {request.title}\n\nDescription:\n{request.description}"
    if request.requirements:
        job_text += f"\n\nRequirements:\n{request.requirements}"
    if request.skills:
        job_text += f"\n\nRequired Skills: {request.skills}"

    vector = await embedding.embed_text(job_text)
    await vector_store.upsert_job(
        request.job_id, vector, request.title,
        request.description, request.requirements, request.skills
    )
    return VectorizeResponse(status="ok")
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
python -m pytest tests/test_vectorize.py -v
```
Expected: 6 PASSED

- [ ] **Step 5: Commit**

```bash
git add routers/vectorize.py tests/test_vectorize.py
git commit -m "feat: add vectorize router for resume and job endpoints"
```

---

### Task 9: Match Router (TDD)

**Files:**
- Create: `ai-matching-service/routers/match.py`
- Create: `ai-matching-service/tests/test_match.py`

- [ ] **Step 1: Write the failing tests**

Create `ai-matching-service/tests/test_match.py`:

```python
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from schemas import MatchScore


def _make_scored_point(resume_id: str, score: float, raw_text: str):
    p = MagicMock()
    p.id = resume_id
    p.score = score
    p.payload = {"resume_id": resume_id, "raw_text": raw_text}
    return p


def _make_job_record(job_id: str):
    r = MagicMock()
    r.vector = [0.5] * 1536
    r.payload = {
        "job_id": job_id, "title": "Engineer",
        "description": "Build things", "requirements": "5yr", "skills": "Java",
    }
    return r


def test_match_returns_ranked_results(client):
    job_record = _make_job_record("job-001")
    candidates = [
        _make_scored_point("resume-a", 0.85, "Alice Java"),
        _make_scored_point("resume-b", 0.80, "Bob Python"),
    ]
    scores = [
        MatchScore(score=90, reasoning="Strong match", highlights=["Java"]),
        MatchScore(score=60, reasoning="Partial", highlights=[]),
    ]

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=AsyncMock(return_value=candidates)), \
         patch("services.llm.score_match", new=AsyncMock(side_effect=scores)):
        response = client.post("/match", json={"job_id": "job-001", "top_k": 2})

    assert response.status_code == 200
    data = response.json()
    assert data["job_id"] == "job-001"
    assert len(data["results"]) == 2
    # Sorted by llm_score descending
    assert data["results"][0]["llm_score"] == 90
    assert data["results"][1]["llm_score"] == 60


def test_match_returns_404_when_job_not_vectorized(client):
    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=None)):
        response = client.post("/match", json={"job_id": "missing-job", "top_k": 5})

    assert response.status_code == 404
    assert "not found" in response.json()["detail"].lower()


def test_match_returns_empty_results_when_no_resumes(client):
    job_record = _make_job_record("job-002")

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=AsyncMock(return_value=[])):
        response = client.post("/match", json={"job_id": "job-002", "top_k": 10})

    assert response.status_code == 200
    assert response.json()["results"] == []


def test_match_overfetches_from_qdrant(client):
    job_record = _make_job_record("job-003")
    search_mock = AsyncMock(return_value=[])

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=search_mock):
        client.post("/match", json={"job_id": "job-003", "top_k": 5})

    # Should search for top_k * 2 = 10 candidates
    search_mock.assert_called_once_with(job_record.vector, 10)


def test_match_result_includes_vector_score(client):
    job_record = _make_job_record("job-004")
    candidates = [_make_scored_point("resume-x", 0.93, "Expert")]
    mock_score = MatchScore(score=88, reasoning="Great", highlights=["skill"])

    with patch("services.vector_store.get_job_record", new=AsyncMock(return_value=job_record)), \
         patch("services.vector_store.search_resumes", new=AsyncMock(return_value=candidates)), \
         patch("services.llm.score_match", new=AsyncMock(return_value=mock_score)):
        response = client.post("/match", json={"job_id": "job-004", "top_k": 1})

    result = response.json()["results"][0]
    assert result["resume_id"] == "resume-x"
    assert result["vector_score"] == 0.93
    assert result["llm_score"] == 88


def test_match_top_k_max_50(client):
    response = client.post("/match", json={"job_id": "job-999", "top_k": 51})
    assert response.status_code == 422
```

- [ ] **Step 2: Run to confirm they fail**

```bash
python -m pytest tests/test_match.py -v
```
Expected: failures because `routers/match.py` doesn't exist

- [ ] **Step 3: Implement `routers/match.py`**

```python
import asyncio
from fastapi import APIRouter, HTTPException
from schemas import MatchRequest, MatchResponse, MatchResultItem
from services import vector_store, llm

router = APIRouter()


@router.post("/match", response_model=MatchResponse)
async def match_resumes(request: MatchRequest):
    job_record = await vector_store.get_job_record(request.job_id)
    if job_record is None:
        raise HTTPException(status_code=404, detail="Job not found in vector store")

    candidates = await vector_store.search_resumes(job_record.vector, request.top_k * 2)
    if not candidates:
        return MatchResponse(job_id=request.job_id, results=[])

    payload = job_record.payload
    job_text = f"Title: {payload['title']}\n\nDescription:\n{payload['description']}"
    if payload.get("requirements"):
        job_text += f"\n\nRequirements:\n{payload['requirements']}"
    if payload.get("skills"):
        job_text += f"\n\nRequired Skills: {payload['skills']}"

    async def score_candidate(candidate):
        resume_text = candidate.payload.get("raw_text", "")
        score = await llm.score_match(job_text, resume_text)
        return MatchResultItem(
            resume_id=str(candidate.id),
            vector_score=candidate.score,
            llm_score=score.score,
            reasoning=score.reasoning,
            highlights=score.highlights,
        )

    results = await asyncio.gather(*[score_candidate(c) for c in candidates])
    ranked = sorted(results, key=lambda r: r.llm_score, reverse=True)
    return MatchResponse(job_id=request.job_id, results=ranked[: request.top_k])
```

- [ ] **Step 4: Run all Python tests to confirm they pass**

```bash
python -m pytest tests/ -v
```
Expected: All tests PASSED (no failures)

- [ ] **Step 5: Commit**

```bash
git add routers/match.py tests/test_match.py
git commit -m "feat: add match router with LLM re-ranking and over-fetch"
```

---

## Chunk 3: Spring Boot Integration

### Task 10: JobDescriptionSavedEvent + JobService Publishing

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/job/JobDescriptionSavedEvent.java`
- Modify: `ai-hiring-backend/src/main/java/com/aihiring/job/JobService.java`

- [ ] **Step 1: Write the failing test**

Create `ai-hiring-backend/src/test/java/com/aihiring/job/JobServiceEventTest.java`:

```java
package com.aihiring.job;

import com.aihiring.job.dto.CreateJobRequest;
import com.aihiring.job.dto.UpdateJobRequest;
import com.aihiring.department.DepartmentRepository;
import com.aihiring.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceEventTest {

    @Mock JobRepository jobRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks JobService jobService;

    @Test
    void create_publishesJobDescriptionSavedEvent() {
        var dept = new com.aihiring.department.Department();
        dept.setId(UUID.randomUUID());
        dept.setName("Engineering");

        var user = new com.aihiring.user.User();
        user.setId(UUID.randomUUID());

        var savedJob = new JobDescription();
        savedJob.setId(UUID.randomUUID());
        savedJob.setTitle("Engineer");
        savedJob.setDescription("Build things");
        savedJob.setRequirements("5yr");
        savedJob.setSkills("[\"Java\"]");
        savedJob.setDepartment(dept);
        savedJob.setCreatedBy(user);

        when(departmentRepository.findById(any())).thenReturn(Optional.of(dept));
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(jobRepository.save(any())).thenReturn(savedJob);

        var request = new CreateJobRequest();
        request.setTitle("Engineer");
        request.setDescription("Build things");
        request.setRequirements("5yr");
        request.setSkills("[\"Java\"]");
        request.setDepartmentId(dept.getId());

        jobService.create(request, user.getId());

        var captor = ArgumentCaptor.forClass(JobDescriptionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.getJobId()).isEqualTo(savedJob.getId());
        assertThat(event.getTitle()).isEqualTo("Engineer");
        assertThat(event.getDescription()).isEqualTo("Build things");
    }

    @Test
    void update_publishesJobDescriptionSavedEvent() {
        var dept = new com.aihiring.department.Department();
        dept.setId(UUID.randomUUID());
        dept.setName("Engineering");

        var user = new com.aihiring.user.User();
        user.setId(UUID.randomUUID());

        var existingJob = new JobDescription();
        existingJob.setId(UUID.randomUUID());
        existingJob.setTitle("Old title");
        existingJob.setDescription("Old desc");
        existingJob.setDepartment(dept);
        existingJob.setCreatedBy(user);

        when(jobRepository.findById(any())).thenReturn(Optional.of(existingJob));
        when(jobRepository.save(any())).thenReturn(existingJob);

        var request = new UpdateJobRequest();
        request.setTitle("New title");

        jobService.update(existingJob.getId(), request);

        verify(eventPublisher).publishEvent(any(JobDescriptionSavedEvent.class));
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
cd ai-hiring-backend
./gradlew test --tests "com.aihiring.job.JobServiceEventTest" 2>&1 | tail -20
```
Expected: Compilation error — `JobDescriptionSavedEvent` and `ApplicationEventPublisher` in `JobService` not found

- [ ] **Step 3: Create `JobDescriptionSavedEvent.java`**

```java
package com.aihiring.job;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class JobDescriptionSavedEvent extends ApplicationEvent {
    private final UUID jobId;
    private final String title;
    private final String description;
    private final String requirements;
    private final String skills;

    public JobDescriptionSavedEvent(Object source, UUID jobId, String title,
                                     String description, String requirements, String skills) {
        super(source);
        this.jobId = jobId;
        this.title = title;
        this.description = description;
        this.requirements = requirements;
        this.skills = skills;
    }
}
```

- [ ] **Step 4: Update `JobService.java` — inject publisher and publish events**

Add `ApplicationEventPublisher eventPublisher` to the constructor (via Lombok `@RequiredArgsConstructor`), then at the end of `create()`:

```java
// After jobRepository.save(job):
eventPublisher.publishEvent(new JobDescriptionSavedEvent(
    this, saved.getId(), saved.getTitle(), saved.getDescription(),
    saved.getRequirements(), saved.getSkills()
));
return saved;
```

And at the end of `update()`:

```java
// After jobRepository.save(job):
JobDescription saved = jobRepository.save(job);
eventPublisher.publishEvent(new JobDescriptionSavedEvent(
    this, saved.getId(), saved.getTitle(), saved.getDescription(),
    saved.getRequirements(), saved.getSkills()
));
return saved;
```

Full updated `create` method in `JobService.java`:
```java
@Transactional
public JobDescription create(CreateJobRequest request, UUID createdByUserId) {
    var department = departmentRepository.findById(request.getDepartmentId())
        .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    var user = userRepository.findById(createdByUserId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));

    JobDescription job = new JobDescription();
    job.setTitle(request.getTitle());
    job.setDescription(request.getDescription());
    job.setRequirements(request.getRequirements());
    job.setSkills(request.getSkills());
    job.setEducation(request.getEducation());
    job.setExperience(request.getExperience());
    job.setSalaryRange(request.getSalaryRange());
    job.setLocation(request.getLocation());
    job.setStatus(JobStatus.DRAFT);
    job.setDepartment(department);
    job.setCreatedBy(user);

    JobDescription saved = jobRepository.save(job);
    eventPublisher.publishEvent(new JobDescriptionSavedEvent(
        this, saved.getId(), saved.getTitle(), saved.getDescription(),
        saved.getRequirements(), saved.getSkills()
    ));
    return saved;
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.aihiring.job.JobServiceEventTest" 2>&1 | tail -20
```
Expected: 2 tests PASSED

- [ ] **Step 6: Run existing JobService tests to make sure nothing broke**

```bash
./gradlew test --tests "com.aihiring.job.JobServiceTest" 2>&1 | tail -20
```
Expected: all PASSED

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/aihiring/job/JobDescriptionSavedEvent.java \
        src/main/java/com/aihiring/job/JobService.java \
        src/test/java/com/aihiring/job/JobServiceEventTest.java
git commit -m "feat: publish JobDescriptionSavedEvent on JD create and update"
```

---

### Task 11: AiMatchingClient (TDD)

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/AiMatchingClient.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/VectorizeResumeRequest.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/VectorizeJobRequest.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/AiMatchRequest.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/AiMatchResponse.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/AiMatchResultItem.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/matching/AiMatchingClientTest.java`

- [ ] **Step 1: Create DTOs**

`dto/VectorizeResumeRequest.java`:
```java
package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VectorizeResumeRequest {
    @JsonProperty("resume_id")
    private UUID resumeId;
    @JsonProperty("raw_text")
    private String rawText;
}
```

`dto/VectorizeJobRequest.java`:
```java
package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VectorizeJobRequest {
    @JsonProperty("job_id")
    private UUID jobId;
    private String title;
    private String description;
    private String requirements;
    private String skills;
}
```

`dto/AiMatchRequest.java`:
```java
package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AiMatchRequest {
    @JsonProperty("job_id")
    private UUID jobId;
    @JsonProperty("top_k")
    private int topK;
}
```

`dto/AiMatchResultItem.java`:
```java
package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class AiMatchResultItem {
    @JsonProperty("resume_id")
    private String resumeId;
    @JsonProperty("vector_score")
    private double vectorScore;
    @JsonProperty("llm_score")
    private int llmScore;
    private String reasoning;
    private List<String> highlights;
}
```

`dto/AiMatchResponse.java`:
```java
package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class AiMatchResponse {
    @JsonProperty("job_id")
    private String jobId;
    private List<AiMatchResultItem> results;
}
```

- [ ] **Step 2: Write the failing unit tests**

Create `src/test/java/com/aihiring/matching/AiMatchingClientTest.java`:

```java
package com.aihiring.matching;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertDoesNotThrow;
import static org.assertj.core.api.Assertions.assertThat;

class AiMatchingClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    AiMatchingClient client;

    @BeforeEach
    void setUp() {
        client = new AiMatchingClient(
            RestClient.builder(),
            "http://localhost:" + wm.getPort()
        );
    }

    @Test
    void vectorizeResume_callsCorrectEndpointWithPayload() {
        UUID resumeId = UUID.randomUUID();
        wm.stubFor(post(urlEqualTo("/internal/vectorize/resume"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\"}")));

        client.vectorizeResume(resumeId, "John Smith Java developer");

        wm.verify(postRequestedFor(urlEqualTo("/internal/vectorize/resume"))
            .withRequestBody(matchingJsonPath("$.resume_id"))
            .withRequestBody(matchingJsonPath("$.raw_text", equalTo("John Smith Java developer"))));
    }

    @Test
    void vectorizeResume_doesNotThrowWhenServiceReturnsError() {
        wm.stubFor(post(urlEqualTo("/internal/vectorize/resume"))
            .willReturn(aResponse().withStatus(500)));

        assertDoesNotThrow(() -> client.vectorizeResume(UUID.randomUUID(), "text"));
    }

    @Test
    void vectorizeJob_callsCorrectEndpoint() {
        UUID jobId = UUID.randomUUID();
        wm.stubFor(post(urlEqualTo("/internal/vectorize/job"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\"}")));

        client.vectorizeJob(jobId, "Engineer", "Build things", "5yr", "Java");

        wm.verify(postRequestedFor(urlEqualTo("/internal/vectorize/job"))
            .withRequestBody(matchingJsonPath("$.job_id"))
            .withRequestBody(matchingJsonPath("$.title", equalTo("Engineer"))));
    }

    @Test
    void vectorizeJob_doesNotThrowWhenServiceReturnsError() {
        wm.stubFor(post(urlEqualTo("/internal/vectorize/job"))
            .willReturn(aResponse().withStatus(503)));

        assertDoesNotThrow(() ->
            client.vectorizeJob(UUID.randomUUID(), "T", "D", null, null));
    }

    @Test
    void match_returnsDeserializedResponse() {
        UUID jobId = UUID.randomUUID();
        wm.stubFor(post(urlEqualTo("/match"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"job_id":"%s","results":[
                      {"resume_id":"r1","vector_score":0.9,"llm_score":85,
                       "reasoning":"Good","highlights":["Java"]}
                    ]}""".formatted(jobId))));

        var response = client.match(jobId, 5);

        assertThat(response.getJobId()).isEqualTo(jobId.toString());
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getLlmScore()).isEqualTo(85);
    }
}
```

- [ ] **Step 3: Run to confirm tests fail**

```bash
./gradlew test --tests "com.aihiring.matching.AiMatchingClientTest" 2>&1 | tail -20
```
Expected: Compilation error — `AiMatchingClient` not found

- [ ] **Step 4: Implement `AiMatchingClient.java`**

Accept `RestClient.Builder` as a constructor parameter so tests can inject a builder pointing at WireMock:

```java
package com.aihiring.matching;

import com.aihiring.matching.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Slf4j
@Component
public class AiMatchingClient {

    private final RestClient restClient;

    public AiMatchingClient(
        RestClient.Builder builder,
        @Value("${ai.matching.base-url:http://localhost:8001}") String baseUrl
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public void vectorizeResume(UUID resumeId, String rawText) {
        try {
            restClient.post()
                .uri("/internal/vectorize/resume")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VectorizeResumeRequest(resumeId, rawText))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Failed to vectorize resume {}: {}", resumeId, e.getMessage());
        }
    }

    public void vectorizeJob(UUID jobId, String title, String description,
                              String requirements, String skills) {
        try {
            restClient.post()
                .uri("/internal/vectorize/job")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VectorizeJobRequest(jobId, title, description, requirements, skills))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException e) {
            log.warn("Failed to vectorize job {}: {}", jobId, e.getMessage());
        }
    }

    public AiMatchResponse match(UUID jobId, int topK) {
        return restClient.post()
            .uri("/match")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new AiMatchRequest(jobId, topK))
            .retrieve()
            .body(AiMatchResponse.class);
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.aihiring.matching.AiMatchingClientTest" 2>&1 | tail -20
```
Expected: 5 tests PASSED

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aihiring/matching/ \
        src/test/java/com/aihiring/matching/AiMatchingClientTest.java
git commit -m "feat: add AiMatchingClient with RestClient for AI service calls"
```

---

### Task 12: Event Listener (TDD)

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/AiMatchingEventListener.java`
- Modify: `ai-hiring-backend/src/main/java/com/aihiring/AiHiringApplication.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/matching/AiMatchingEventListenerTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/aihiring/matching/AiMatchingEventListenerTest.java`:

```java
package com.aihiring.matching;

import com.aihiring.job.JobDescriptionSavedEvent;
import com.aihiring.resume.ResumeUploadedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiMatchingEventListenerTest {

    @Mock AiMatchingClient client;
    @InjectMocks AiMatchingEventListener listener;

    @Test
    void onResumeUploaded_callsVectorizeResume() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, "John Smith resume", "txt");

        listener.onResumeUploaded(event);

        verify(client).vectorizeResume(resumeId, "John Smith resume");
    }

    @Test
    void onResumeUploaded_withNullText_skipsVectorize() {
        UUID resumeId = UUID.randomUUID();
        var event = new ResumeUploadedEvent(this, resumeId, null, "pdf");

        listener.onResumeUploaded(event);

        verify(client, never()).vectorizeResume(any(), any());
    }

    @Test
    void onJobSaved_callsVectorizeJob() {
        UUID jobId = UUID.randomUUID();
        var event = new JobDescriptionSavedEvent(
            this, jobId, "Engineer", "Build things", "5yr", "[\"Java\"]"
        );

        listener.onJobSaved(event);

        verify(client).vectorizeJob(jobId, "Engineer", "Build things", "5yr", "[\"Java\"]");
    }
}
```

- [ ] **Step 2: Run to confirm it fails**

```bash
./gradlew test --tests "com.aihiring.matching.AiMatchingEventListenerTest" 2>&1 | tail -20
```
Expected: Compilation error — `AiMatchingEventListener` not found

- [ ] **Step 3: Implement `AiMatchingEventListener.java`**

```java
package com.aihiring.matching;

import com.aihiring.job.JobDescriptionSavedEvent;
import com.aihiring.resume.ResumeUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiMatchingEventListener {

    private final AiMatchingClient client;

    @Async
    @EventListener
    public void onResumeUploaded(ResumeUploadedEvent event) {
        if (event.getRawText() == null) {
            log.debug("Skipping vectorization for resume {} — no raw text", event.getResumeId());
            return;
        }
        log.info("Vectorizing resume {}", event.getResumeId());
        client.vectorizeResume(event.getResumeId(), event.getRawText());
    }

    @Async
    @EventListener
    public void onJobSaved(JobDescriptionSavedEvent event) {
        log.info("Vectorizing job {}", event.getJobId());
        client.vectorizeJob(
            event.getJobId(), event.getTitle(), event.getDescription(),
            event.getRequirements(), event.getSkills()
        );
    }
}
```

- [ ] **Step 4: Add `@EnableAsync` to `AiHiringApplication.java`**

Add `@EnableAsync` annotation:

```java
@SpringBootApplication
@EnableAsync
public class AiHiringApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiHiringApplication.class, args);
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./gradlew test --tests "com.aihiring.matching.AiMatchingEventListenerTest" 2>&1 | tail -20
```
Expected: 3 tests PASSED

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aihiring/matching/AiMatchingEventListener.java \
        src/main/java/com/aihiring/AiHiringApplication.java \
        src/test/java/com/aihiring/matching/AiMatchingEventListenerTest.java
git commit -m "feat: add async event listener for resume and JD vectorization"
```

---

### Task 13: MatchController + MatchService (TDD)

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/MatchService.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/MatchController.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/MatchRequest.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/MatchResponse.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/matching/dto/MatchResultItem.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/matching/MatchServiceTest.java`

- [ ] **Step 1: Write failing test for MatchService**

Create `src/test/java/com/aihiring/matching/MatchServiceTest.java`:

```java
package com.aihiring.matching;

import com.aihiring.matching.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock AiMatchingClient client;
    @InjectMocks MatchService matchService;

    @Test
    void match_delegatesToClientAndReturnsResponse() throws Exception {
        UUID jobId = UUID.randomUUID();
        // Build a properly populated AiMatchResponse via Jackson deserialization
        String json = """
            {"job_id": "%s", "results": [
              {"resume_id": "r1", "vector_score": 0.9, "llm_score": 80,
               "reasoning": "Good match", "highlights": ["Java"]}
            ]}""".formatted(jobId);
        AiMatchResponse aiResponse = new ObjectMapper().readValue(json, AiMatchResponse.class);
        when(client.match(eq(jobId), eq(10))).thenReturn(aiResponse);

        AiMatchResponse result = matchService.match(jobId, 10);

        assertThat(result).isSameAs(aiResponse);
        assertThat(result.getResults()).hasSize(1);
        verify(client).match(jobId, 10);
    }
}
```

- [ ] **Step 2: Implement `MatchService.java`**

```java
package com.aihiring.matching;

import com.aihiring.matching.dto.AiMatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final AiMatchingClient client;

    public AiMatchResponse match(UUID jobId, int topK) {
        return client.match(jobId, topK);
    }
}
```

- [ ] **Step 3: Create public-facing DTOs**

`dto/MatchRequest.java`:
```java
package com.aihiring.matching.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class MatchRequest {
    @NotNull
    private UUID jobId;
    @Min(1) @Max(50)
    private int topK = 10;
}
```

`dto/MatchResultItem.java`:
```java
package com.aihiring.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class MatchResultItem {
    private String resumeId;
    private double vectorScore;
    private int llmScore;
    private String reasoning;
    private List<String> highlights;
}
```

`dto/MatchResponse.java`:
```java
package com.aihiring.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class MatchResponse {
    private UUID jobId;
    private List<MatchResultItem> results;
}
```

- [ ] **Step 4: Implement `MatchController.java`**

```java
package com.aihiring.matching;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.matching.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PostMapping
    @PreAuthorize("hasAuthority('job:read')")
    public ApiResponse<MatchResponse> match(@Valid @RequestBody MatchRequest request) {
        AiMatchResponse aiResponse = matchService.match(request.getJobId(), request.getTopK());

        var results = aiResponse.getResults().stream()
            .map(item -> new MatchResultItem(
                item.getResumeId(), item.getVectorScore(), item.getLlmScore(),
                item.getReasoning(), item.getHighlights()
            ))
            .collect(Collectors.toList());

        return ApiResponse.success(new MatchResponse(
            UUID.fromString(aiResponse.getJobId()), results
        ));
    }
}
```

- [ ] **Step 5: Run unit tests to confirm they pass**

```bash
./gradlew test --tests "com.aihiring.matching.MatchServiceTest" 2>&1 | tail -20
```
Expected: 1 test PASSED

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aihiring/matching/ \
        src/test/java/com/aihiring/matching/MatchServiceTest.java
git commit -m "feat: add MatchController, MatchService, and match DTOs"
```

---

### Task 14: Integration Tests (WireMock) + Configuration

**Files:**
- Modify: `ai-hiring-backend/build.gradle.kts`
- Modify: `ai-hiring-backend/src/main/resources/application.yml`
- Modify: `ai-hiring-backend/src/test/resources/application-test.yml`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/matching/MatchControllerIntegrationTest.java`

- [ ] **Step 1: Add WireMock dependency to `build.gradle.kts`**

Add to the `dependencies` block:
```kotlin
testImplementation("org.wiremock.integrations:wiremock-spring-boot:3.2.0")
```

- [ ] **Step 2: Update `application.yml`**

Add at the end of `src/main/resources/application.yml`:
```yaml
ai:
  matching:
    base-url: ${AI_MATCHING_BASE_URL:http://localhost:8001}
```

- [ ] **Step 3: No change needed to `application-test.yml`**

The WireMock Spring Boot integration injects the dynamic WireMock port directly into the `ai.matching.base-url` property via `@ConfigureWireMock`. Do not add a hardcoded URL to `application-test.yml`.

- [ ] **Step 4: Write the failing integration test**

Create `src/test/java/com/aihiring/matching/MatchControllerIntegrationTest.java`:

```java
package com.aihiring.matching;

import com.aihiring.common.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;
import com.github.tomakehurst.wiremock.WireMockServer;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnableWireMock({
    @ConfigureWireMock(name = "ai-matching", property = "ai.matching.base-url")
})
class MatchControllerIntegrationTest {

    @InjectWireMock("ai-matching")
    WireMockServer wireMock;

    @Autowired
    private MockMvc mockMvc;

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "admin", "password", true, null,
            List.of("SUPER_ADMIN"),
            List.of("job:read", "job:manage", "resume:read")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    @Test
    void match_withValidJob_returnsResults() throws Exception {
        UUID jobId = UUID.randomUUID();
        String aiResponseBody = """
            {
              "job_id": "%s",
              "results": [
                {
                  "resume_id": "resume-001",
                  "vector_score": 0.92,
                  "llm_score": 87,
                  "reasoning": "Strong match",
                  "highlights": ["Java", "Spring Boot"]
                }
              ]
            }
            """.formatted(jobId);

        wireMock.stubFor(post(urlEqualTo("/match"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(aiResponseBody)));

        mockMvc.perform(post("/api/match")
                .with(adminUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\": \"%s\", \"topK\": 10}".formatted(jobId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.results[0].llmScore").value(87))
            .andExpect(jsonPath("$.data.results[0].resumeId").value("resume-001"));
    }

    @Test
    void match_whenAiServiceReturns404_propagates422() throws Exception {
        wireMock.stubFor(post(urlEqualTo("/match"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"detail\": \"Job not found in vector store\"}")));

        mockMvc.perform(post("/api/match")
                .with(adminUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\": \"%s\", \"topK\": 5}".formatted(UUID.randomUUID())))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void match_withoutPermission_returns403() throws Exception {
        UserDetailsImpl viewer = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "viewer", "password", true, null,
            List.of("USER"),
            List.of("resume:read")  // missing job:read
        );

        mockMvc.perform(post("/api/match")
                .with(SecurityMockMvcRequestPostProcessors.user(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\": \"%s\", \"topK\": 5}".formatted(UUID.randomUUID())))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 5: Update `MatchController.java` to add 404 → 422 and 503 error handling**

Replace the `match()` method body in `MatchController.java` with the complete version below (adds imports and try/catch around the AI call):

```java
package com.aihiring.matching;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.matching.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    @PostMapping
    @PreAuthorize("hasAuthority('job:read')")
    public ApiResponse<MatchResponse> match(@Valid @RequestBody MatchRequest request) {
        AiMatchResponse aiResponse;
        try {
            aiResponse = matchService.match(request.getJobId(), request.getTopK());
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(422, "Job not ready for matching, please try again shortly");
        } catch (ResourceAccessException e) {
            throw new BusinessException(503, "AI matching service unavailable");
        }

        var results = aiResponse.getResults().stream()
            .map(item -> new MatchResultItem(
                item.getResumeId(), item.getVectorScore(), item.getLlmScore(),
                item.getReasoning(), item.getHighlights()
            ))
            .collect(Collectors.toList());

        return ApiResponse.success(new MatchResponse(
            UUID.fromString(aiResponse.getJobId()), results
        ));
    }
}
```

- [ ] **Step 6: Run all Spring Boot tests**

```bash
./gradlew test 2>&1 | tail -30
```
Expected: All tests PASSED

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts \
        src/main/resources/application.yml \
        src/test/resources/application-test.yml \
        src/test/java/com/aihiring/matching/MatchControllerIntegrationTest.java
git commit -m "feat: add WireMock integration tests for MatchController"
```

---

### Task 15: Docker Compose

**Files:**
- Modify: `docker-compose.yml` (or create if not present)

- [ ] **Step 1: Check if docker-compose.yml exists**

```bash
ls /home/ubuntu/WS/ai-hiring/docker-compose.yml
```

- [ ] **Step 2: Add qdrant and ai-matching services**

Add to `docker-compose.yml` under `services:`:

```yaml
  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"   # local dev only; omit in production
    volumes:
      - qdrant_data:/qdrant/storage

  ai-matching:
    build: ./ai-matching-service
    ports:
      - "8001:8001"   # local dev only; omit in production
    environment:
      - LLM_MODEL=gpt-4o-mini
      - EMBEDDING_MODEL=text-embedding-3-small
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - QDRANT_HOST=qdrant
    depends_on:
      - qdrant
```

Add to `volumes:` section:
```yaml
  qdrant_data:
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat: add qdrant and ai-matching-service to docker-compose"
```

---

### Task 16: Final Verification

- [ ] **Step 1: Run full Python test suite**

```bash
cd ai-matching-service
python -m pytest tests/ -v
```
Expected: All tests PASSED

- [ ] **Step 2: Run full Spring Boot test suite**

```bash
cd ai-hiring-backend
./gradlew test 2>&1 | tail -30
```
Expected: All tests PASSED, BUILD SUCCESSFUL

- [ ] **Step 3: Use finishing-a-development-branch skill**

Invoke `superpowers:finishing-a-development-branch` to push PR or merge.
