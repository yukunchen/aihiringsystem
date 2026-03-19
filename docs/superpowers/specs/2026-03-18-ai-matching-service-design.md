# AI Matching Service Design

**Project**: AI Hiring Platform
**Service**: AI Matching Service (standalone Python/FastAPI)
**Date**: 2026-03-18

---

## 1. Overview

Standalone Python/FastAPI service responsible for vectorizing resumes and job descriptions, storing vectors in Qdrant, and scoring resume-JD match quality using an LLM. Designed as a separate process from the Spring Boot monolith.

**Dependencies:** Resume Module ✅, JD Module ✅ (both complete in Spring Boot)

**Scope:**
- Receive vectorization webhooks from Spring Boot (on resume upload and JD create/update)
- Store vectors in Qdrant
- Accept match requests (JD ID) from Spring Boot, return top-K ranked resumes with scores and reasoning
- LLM provider and embedding model swappable via environment variables — zero code changes needed

**Out of scope:** Resume parsing, JD CRUD, user authentication (handled by Spring Boot).

**Tech stack:** Python 3.12, FastAPI, litellm, qdrant-client, pydantic-settings, pytest.

---

## 2. Architecture

### Service Layout

```
ai-matching-service/
├── main.py                  # FastAPI app, router registration, startup (Qdrant collection init)
├── config.py                # Settings via pydantic-settings (env vars)
├── schemas.py               # Pydantic request/response models
├── routers/
│   ├── match.py             # POST /match
│   └── vectorize.py         # POST /internal/vectorize/resume, /internal/vectorize/job
├── services/
│   ├── embedding.py         # Embed text via litellm.aembedding()
│   ├── vector_store.py      # Qdrant client: upsert and search
│   └── llm.py               # Score match via litellm.acompletion()
├── requirements.txt
├── Dockerfile
└── tests/
    ├── test_match.py
    ├── test_vectorize.py
    └── conftest.py
```

### Network Topology

```
Public Internet
      │
   Port 80/443  ← only public-facing port (AWS Security Group)
      │
   Nginx/Kong
      │
   [Docker internal network]
      ├── Spring Boot :8080
      │     ├── calls :8001 for vectorize webhooks (on save events)
      │     └── calls :8001/match (on user match request)
      ├── AI Matching Service :8001
      │     └── calls :6333 (Qdrant)
      ├── Qdrant :6333
      └── PostgreSQL :5432
```

No additional AWS Security Group ports needed. All inter-service calls are internal Docker network.

### Spring Boot Integration Points

Two new components added to Spring Boot (minimal changes):

1. **`ResumeUploadedEvent` listener** — already published by `ResumeService`. New `AiMatchingClient` bean listens and calls `POST /internal/vectorize/resume`.
2. **`JobDescriptionSavedEvent`** — new Spring `ApplicationEvent`, published by `JobService` on create and update. `AiMatchingClient` listens and calls `POST /internal/vectorize/job`.
3. **`MatchController` + `MatchService`** — new Spring Boot endpoints under `/api/match`. `MatchService` calls `POST /match` on the AI service and returns results wrapped in `ApiResponse`.

---

## 3. API Endpoints

### Vectorization (internal, called by Spring Boot)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/internal/vectorize/resume` | Embed and store resume vector |
| POST | `/internal/vectorize/job` | Embed and store JD vector |

**Vectorize resume request:**
```json
{ "resume_id": "UUID", "raw_text": "John Smith\nSoftware Engineer..." }
```

**Vectorize job request:**
```json
{
  "job_id": "UUID",
  "title": "Senior Java Developer",
  "description": "We are looking for...",
  "requirements": "5+ years Java experience",
  "skills": "[\"Java\", \"Spring Boot\"]"
}
```

Both return `{ "status": "ok" }`.

**Edge cases:**
- `raw_text` is null (resume with failed text extraction) → return `{ "status": "skipped", "reason": "no text" }` without calling embedding API
- Upsert is idempotent — re-vectorizing the same ID overwrites the existing vector

### Matching (called by Spring Boot)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/match` | Match JD against resumes, return ranked results |
| GET | `/health` | Health check |

**Match request:**
```json
{ "job_id": "UUID", "top_k": 10 }
```

`top_k` defaults to 10 if omitted. Max 50.

**Match response:**
```json
{
  "job_id": "UUID",
  "results": [
    {
      "resume_id": "UUID",
      "vector_score": 0.92,
      "llm_score": 87,
      "reasoning": "Strong Java and Spring Boot background with 5 years experience matching the job requirements. PostgreSQL expertise aligns well.",
      "highlights": ["Java expertise", "Spring Boot", "5 years experience"]
    }
  ]
}
```

`vector_score` = raw cosine similarity from Qdrant (0-1). `llm_score` = LLM-assigned score (0-100). Results sorted by `llm_score` descending.

**Health check response:**
```json
{ "status": "ok", "qdrant": "ok", "llm_model": "gpt-4o-mini", "embedding_model": "text-embedding-3-small" }
```

---

## 4. LLM & Embedding — litellm Abstraction

All LLM and embedding calls go through **litellm**, which provides a unified interface for 100+ providers. Swapping providers requires only environment variable changes — zero code changes.

```python
# services/embedding.py
import litellm

async def embed_text(text: str) -> list[float]:
    response = await litellm.aembedding(
        model=settings.EMBEDDING_MODEL,  # "text-embedding-3-small", "cohere/embed-multilingual-v3", etc.
        input=[text]
    )
    return response.data[0]["embedding"]
```

```python
# services/llm.py
import litellm

async def score_match(job_text: str, resume_text: str) -> MatchScore:
    response = await litellm.acompletion(
        model=settings.LLM_MODEL,  # "gpt-4o-mini", "claude-haiku-4-5-20251001", "ollama/llama3", etc.
        messages=[
            {"role": "system", "content": SCORING_SYSTEM_PROMPT},
            {"role": "user", "content": f"Job:\n{job_text}\n\nResume:\n{resume_text}"}
        ],
        response_format={"type": "json_object"},
        temperature=0.1
    )
    return MatchScore.model_validate_json(response.choices[0].message.content)
```

**LLM scoring prompt:**
```
You are a recruiting assistant. Score how well this resume matches the job description.

Return JSON only:
{
  "score": <integer 0-100>,
  "reasoning": "<2-3 sentences explaining the match quality>",
  "highlights": ["<key matching point>", ...]
}

Score guide: 80-100 = strong match, 60-79 = good match, 40-59 = partial match, 0-39 = weak match.
```

**To swap providers — env var only:**
```env
# OpenAI (default)
LLM_MODEL=gpt-4o-mini
EMBEDDING_MODEL=text-embedding-3-small
OPENAI_API_KEY=sk-...

# Switch to Anthropic — no code changes
LLM_MODEL=claude-haiku-4-5-20251001
EMBEDDING_MODEL=text-embedding-3-small   # keep OpenAI for embeddings
ANTHROPIC_API_KEY=sk-ant-...

# Switch to local (Ollama) — no code changes
LLM_MODEL=ollama/llama3
EMBEDDING_MODEL=ollama/nomic-embed-text
# no API key needed
```

---

## 5. Vector Store — Qdrant

Two collections, created at startup if they don't exist:

| Collection | Vector size | Distance | Payload |
|-----------|-------------|----------|---------|
| `resumes` | 1536 | Cosine | `{"resume_id": "UUID"}` |
| `jobs` | 1536 | Cosine | `{"job_id": "UUID"}` |

Vector size 1536 matches `text-embedding-3-small`. If switching to a different embedding model with different dimensions, recreate collections or configure via env var.

```python
# services/vector_store.py
from qdrant_client import AsyncQdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct

async def upsert_resume(resume_id: str, vector: list[float]):
    await client.upsert(
        collection_name="resumes",
        points=[PointStruct(id=resume_id, vector=vector, payload={"resume_id": resume_id})]
    )

async def search_resumes(job_vector: list[float], top_k: int) -> list[ScoredPoint]:
    return await client.search(
        collection_name="resumes",
        query_vector=job_vector,
        limit=top_k
    )
```

---

## 6. Matching Flow

```
POST /match {job_id, top_k}
  │
  ├── 1. Fetch JD vector from Qdrant jobs collection
  │         └── 404 if not found → "Job not ready for matching, try again later"
  │
  ├── 2. Search Qdrant resumes collection (cosine similarity, top_k * 2 candidates)
  │         └── over-fetch (top_k * 2) to account for any scoring reordering
  │
  ├── 3. For each candidate resume:
  │         └── Call LLM to score match (parallel async calls)
  │
  ├── 4. Sort by llm_score descending, take top_k
  │
  └── 5. Return results
```

LLM calls in step 3 run concurrently with `asyncio.gather()` to minimize latency.

**Job text construction for LLM:**
```python
job_text = f"Title: {job.title}\n\nDescription:\n{job.description}"
if job.requirements:
    job_text += f"\n\nRequirements:\n{job.requirements}"
if job.skills:
    job_text += f"\n\nRequired Skills: {job.skills}"
```

---

## 7. Spring Boot Changes

### New files

| File | Responsibility |
|------|---------------|
| `com.aihiring.matching.AiMatchingClient.java` | HTTP client that calls AI service (vectorize + match endpoints) |
| `com.aihiring.matching.AiMatchingEventListener.java` | Listens to `ResumeUploadedEvent` and `JobDescriptionSavedEvent`, calls `AiMatchingClient` |
| `com.aihiring.matching.MatchController.java` | `POST /api/match` — proxies to AI service |
| `com.aihiring.matching.MatchService.java` | Calls `AiMatchingClient.match()`, wraps response |
| `com.aihiring.matching.dto/MatchRequest.java` | `{jobId, topK}` |
| `com.aihiring.matching.dto/MatchResponse.java` | `{jobId, results[]}` |
| `com.aihiring.matching.dto/MatchResultItem.java` | `{resumeId, vectorScore, llmScore, reasoning, highlights}` |

### Modified files

| File | Change |
|------|--------|
| `com.aihiring.job.JobService` | Publish `JobDescriptionSavedEvent` after create and update |
| `com.aihiring.job.JobDescriptionSavedEvent.java` | New ApplicationEvent with `jobId`, `title`, `description`, `requirements`, `skills` |
| `src/main/resources/application.yml` | Add `ai.matching.base-url` config property |

### AiMatchingEventListener behavior

- Listens `@Async` to avoid blocking the save transaction
- If AI service is unreachable: log warning, do not fail the original operation. Vectorization failure is non-fatal.
- `ResumeUploadedEvent.rawText` null → skip vectorization call (matches AI service behavior)

### Match endpoint

```
POST /api/match
Auth: job:read permission
Body: { "jobId": "UUID", "topK": 10 }
Response: ApiResponse<MatchResponse>
```

---

## 8. Configuration

### AI Matching Service (`config.py`)

```python
class Settings(BaseSettings):
    llm_model: str = "gpt-4o-mini"
    embedding_model: str = "text-embedding-3-small"
    vector_size: int = 1536          # must match embedding model output
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333
    port: int = 8001
    log_level: str = "info"

    model_config = SettingsConfigDict(env_file=".env")
```

### Docker Compose additions

```yaml
services:
  qdrant:
    image: qdrant/qdrant:latest
    ports:
      - "6333:6333"   # only for local dev debugging; remove in production
    volumes:
      - qdrant_data:/qdrant/storage

  ai-matching:
    build: ./ai-matching-service
    ports:
      - "8001:8001"   # only for local dev; remove in production
    environment:
      - LLM_MODEL=gpt-4o-mini
      - EMBEDDING_MODEL=text-embedding-3-small
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - QDRANT_HOST=qdrant
    depends_on:
      - qdrant

volumes:
  qdrant_data:
```

In production on the VPS, the `ports` mappings for qdrant and ai-matching are removed — only Nginx (port 80/443) is exposed publicly.

### Spring Boot (`application.yml` addition)

```yaml
ai:
  matching:
    base-url: ${AI_MATCHING_BASE_URL:http://localhost:8001}
```

---

## 9. Error Handling

| Scenario | AI Service Response | Spring Boot Behavior |
|----------|--------------------|--------------------|
| JD not vectorized yet | 404 `{"detail": "Job not found in vector store"}` | Return 422 "Job not ready for matching, please try again shortly" |
| No resumes vectorized | 200 with empty results | Return empty results list |
| AI service unreachable | Connection error | Return 503 "AI matching service unavailable" |
| LLM API error | 502 `{"detail": "LLM scoring failed"}` | Return 502 with error message |
| Vectorization webhook fails | Non-2xx | Log warning, do not fail the original save operation |

---

## 10. Testing Strategy

### AI Matching Service (Python)

| Layer | What | Tools |
|-------|------|-------|
| Unit | `embedding.py` — mock litellm, verify text input | pytest + unittest.mock |
| Unit | `llm.py` — mock litellm, verify prompt structure, parse JSON output | pytest |
| Unit | `vector_store.py` — mock AsyncQdrantClient | pytest |
| Unit | `match` router — mock all services, verify flow | pytest + FastAPI TestClient |
| Unit | `vectorize` router — null raw_text skip, idempotent upsert | pytest |

### Spring Boot

| Layer | What | Tools |
|-------|------|-------|
| Unit | `AiMatchingEventListener` — verify client called on events, null rawText skipped | JUnit 5 + Mockito |
| Unit | `MatchService` — verify client called, response mapped | JUnit 5 + Mockito |
| Integration | `MatchController` — mock AI service with WireMock | SpringBootTest + WireMock |
