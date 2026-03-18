# Resume Module Design

**Project**: AI Hiring Platform
**Module**: Resume Management
**Date**: 2026-03-18

---

## 1. Overview

Resume management module for the AI Hiring Platform. Provides file upload (PDF/Word/TXT), local file storage, basic text extraction, full-text search, and async event hooks for future AI service integration.

**Scope**: Spring Boot side only. AI pipeline (structuring, vectorization) is deferred to the AI Matching Service. This module defines the async event contract but does not implement AI consumers.

**Tech stack**: Java 21, Spring Boot 3.3, Apache PDFBox (PDF), Apache POI (Word), Spring Data JPA, Flyway, PostgreSQL full-text search.

---

## 2. Architecture

### Package Structure

```
com.aihiring.resume/
├── ResumeController.java
├── ResumeService.java
├── ResumeRepository.java
├── Resume.java                    # Entity
├── ResumeStatus.java              # Enum: UPLOADED, TEXT_EXTRACTED, AI_PROCESSED
├── ResumeSource.java              # Enum: MANUAL, BOSS
├── dto/
│   ├── ResumeResponse.java
│   ├── ResumeListResponse.java    # Truncated rawText for list view
│   └── UpdateStructuredRequest.java
├── storage/
│   ├── FileStorageService.java    # Interface
│   └── LocalFileStorageService.java
└── parser/
    ├── TextExtractor.java         # Interface
    ├── PdfTextExtractor.java
    ├── DocxTextExtractor.java
    └── TxtTextExtractor.java
```

### Upload Flow

```
Client (multipart file)
  -> ResumeController.upload()
    -> Validate file (size, type)
    -> FileStorageService.store(file) -> returns stored path
    -> TextExtractor.extract(file) -> returns raw text
    -> Create Resume entity (status=TEXT_EXTRACTED)
    -> ResumeRepository.save()
    -> Publish "resume.uploaded" Spring ApplicationEvent
    -> Return ResumeResponse
```

Upload is synchronous. Text extraction from PDF/Word is fast (sub-second for typical resumes). If text extraction fails, the file is still stored and the resume is saved with `status=UPLOADED` (degraded, not fatal).

### Key Design Decisions

1. **`FileStorageService` interface** -- `store(file)` returns a path, `load(path)` returns a Resource, `delete(path)` removes the file. `LocalFileStorageService` implements with a configurable base directory. Swap to MinIO later by adding a new implementation.

2. **`TextExtractor` interface** -- `extract(InputStream, fileType)` returns String. Three implementations selected by file type.

3. **`ResumeUploadedEvent`** -- Spring `ApplicationEvent` carrying resume ID and raw text. No consumers yet; the AI service integration will listen here.

4. **Full-text search** -- PostgreSQL `tsvector` column with GIN index. Trigger auto-updates on raw_text change.

5. **File naming** -- Stored as `{uuid}.{extension}` to avoid collisions. Original filename preserved in `file_name` column.

6. **No department scoping** -- Resumes are a shared pool visible to all authenticated users with `resume:read` permission. `uploaded_by` tracks who uploaded for audit.

---

## 3. Data Model

### Resume Entity

`Resume extends BaseEntity` (inherits `id`, `createdAt`, `updatedAt` from `com.aihiring.common.entity.BaseEntity`).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK, auto-generated | Inherited from BaseEntity |
| file_name | String | Not null | Original uploaded filename |
| file_path | String | Not null | Storage path on disk |
| file_size | Long | Not null | Bytes |
| file_type | String | Not null | MIME type |
| raw_text | Text | Nullable | Extracted text content |
| source | Enum | Not null, default MANUAL | MANUAL or BOSS |
| status | Enum | Not null, default UPLOADED | UPLOADED, TEXT_EXTRACTED, AI_PROCESSED |
| uploaded_by | UUID | FK -> User, not null | Who uploaded |
| candidate_name | String | Nullable | AI-populated later |
| candidate_phone | String | Nullable | AI-populated later |
| candidate_email | String | Nullable | AI-populated later |
| education | JSONB | Nullable | AI-populated later |
| experience | JSONB | Nullable | AI-populated later |
| projects | JSONB | Nullable | AI-populated later (from parent doc: project name, role, tech stack, description) |
| skills | JSONB | Nullable | AI-populated later, stored as JSON array of strings |
| search_vector | TSVECTOR | Auto-maintained by trigger | Full-text search |
| created_at | Timestamp | Auto-set | |
| updated_at | Timestamp | Auto-set | |

### Status Lifecycle

```
UPLOADED -> TEXT_EXTRACTED -> AI_PROCESSED
```

- `UPLOADED`: File stored, text extraction failed or not yet attempted
- `TEXT_EXTRACTED`: Raw text available, awaiting AI structuring
- `AI_PROCESSED`: Structured fields populated by AI service

---

## 4. API Endpoints

### Resume (`/api/resumes`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/resumes/upload` | `resume:manage` | Upload resume file (multipart) |
| GET | `/api/resumes` | `resume:read` | List resumes (paginated, searchable) |
| GET | `/api/resumes/{id}` | `resume:read` | Get resume detail |
| GET | `/api/resumes/{id}/download` | `resume:read` | Download original file |
| DELETE | `/api/resumes/{id}` | `resume:manage` | Delete resume + file |
| PUT | `/api/resumes/{id}/structured` | `resume:manage` | Update structured fields (AI service) |

### Upload Request

Multipart form data with a single `file` field. Optional query param `source` (defaults to `MANUAL`).

**Validation:**
- Max file size: 10MB
- Allowed types: `application/pdf`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`
- File must not be empty

### List Query Parameters

- `page`, `size`, `sort` -- standard Spring Pageable (same as User module)
- `search` -- full-text search against `raw_text`
- `status` -- filter by processing status
- `source` -- filter by source (MANUAL/BOSS)

### Response Format

All responses wrapped in standard `ApiResponse`:

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

**ResumeResponse (detail):**

```json
{
  "id": "UUID",
  "fileName": "resume.pdf",
  "fileSize": 102400,
  "fileType": "application/pdf",
  "rawText": "full text content",
  "source": "MANUAL",
  "status": "TEXT_EXTRACTED",
  "uploadedBy": { "id": "UUID", "username": "admin" },
  "candidateName": null,
  "candidatePhone": null,
  "candidateEmail": null,
  "education": null,
  "experience": null,
  "projects": null,
  "skills": null,
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

**ResumeListResponse**: Same as above but `rawText` is truncated (first 200 chars). Null-safe: if `rawText` is null (status=UPLOADED), returns null.

### DTO Schemas

**UpdateStructuredRequest (internal, for AI service):**

```json
{
  "candidateName": "string (optional)",
  "candidatePhone": "string (optional)",
  "candidateEmail": "string (optional)",
  "education": [{"school": "...", "degree": "...", "major": "...", "graduationDate": "..."}],
  "experience": [{"company": "...", "title": "...", "startDate": "...", "endDate": "...", "description": "..."}],
  "projects": [{"name": "...", "role": "...", "techStack": ["..."], "description": "..."}],
  "skills": ["Java", "Spring Boot", "PostgreSQL"]
}
```

---

## 5. Database Migration

**`V3__resume_table.sql`:**

```sql
CREATE TYPE resume_source AS ENUM ('MANUAL', 'BOSS');
CREATE TYPE resume_status AS ENUM ('UPLOADED', 'TEXT_EXTRACTED', 'AI_PROCESSED');

CREATE TABLE resumes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    raw_text TEXT,
    source resume_source NOT NULL DEFAULT 'MANUAL',
    status resume_status NOT NULL DEFAULT 'UPLOADED',
    uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    candidate_name VARCHAR(100),
    candidate_phone VARCHAR(50),
    candidate_email VARCHAR(100),
    education JSONB,
    experience JSONB,
    projects JSONB,
    skills JSONB,
    search_vector TSVECTOR,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_resumes_search_vector ON resumes USING GIN (search_vector);

CREATE OR REPLACE FUNCTION resumes_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.raw_text, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER resumes_search_vector_trigger
    BEFORE INSERT OR UPDATE ON resumes
    FOR EACH ROW EXECUTE FUNCTION resumes_search_vector_update();

CREATE INDEX idx_resumes_source ON resumes(source);
CREATE INDEX idx_resumes_status ON resumes(status);
CREATE INDEX idx_resumes_uploaded_by ON resumes(uploaded_by);
CREATE INDEX idx_resumes_created_at ON resumes(created_at DESC);
```

Uses `'simple'` tsvector configuration for mixed Chinese/English content. **Limitation:** `'simple'` tokenizes by whitespace, so Chinese text (no spaces between words) produces large unsearchable tokens. Individual Chinese word searches will not match until `zhparser` or a custom configuration is added. This is acceptable for MVP — most resume text extraction produces space-separated tokens from structured content.

---

## 6. Configuration

**New application.yml properties:**

```yaml
storage:
  local:
    base-dir: ${STORAGE_BASE_DIR:./uploads}

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

**File storage directory:**

```
uploads/
└── resumes/
    ├── a1b2c3d4-...-.pdf
    ├── e5f6g7h8-...-.docx
    └── i9j0k1l2-...-.txt
```

**New dependencies:**

| Library | Purpose |
|---------|---------|
| Apache PDFBox | PDF text extraction |
| Apache POI (poi-ooxml) | Word .docx text extraction |

---

## 7. Error Handling

| Scenario | Response | Code |
|----------|----------|------|
| File too large (>10MB, Spring multipart) | "File size exceeds maximum of 10MB" | 400 |
| Invalid file type | "Unsupported file type. Allowed: PDF, DOCX, TXT" | 400 |
| Empty file | "File is empty" | 400 |
| Text extraction fails | Store file, set status=UPLOADED, log warning | 200 (degraded) |
| Resume not found | "Resume not found" | 404 |
| File missing from disk | Return 404 on download, log error | 404 |
| Disk storage failure | "Failed to store file" | 500 |

Text extraction failure is not fatal. The file is stored and the resume record is created with `status=UPLOADED`. The AI service can attempt extraction later with more robust tools.

**`MaxUploadSizeExceededException` handling:** Add a handler in `GlobalExceptionHandler` to catch Spring's `MaxUploadSizeExceededException` and return the standard `ApiResponse` wrapper with code 400 and message "File size exceeds maximum of 10MB". Without this, oversized uploads return a raw Spring error response.

---

## 8. Testing Strategy

| Layer | What | Tools |
|-------|------|-------|
| Unit | ResumeService (upload, list, delete logic) | JUnit 5 + Mockito |
| Unit | PdfTextExtractor, DocxTextExtractor, TxtTextExtractor | JUnit 5 + sample files |
| Unit | LocalFileStorageService (store, load, delete) | JUnit 5 + temp directory |
| Integration | ResumeController (upload, list, download, delete) | SpringBootTest + MockMvc + H2 |
| Integration | ResumeRepository (CRUD, pagination, filtering) | DataJpaTest + H2 |
| Integration | Full-text search | SpringBootTest + Testcontainers (PostgreSQL) |

**Test fixtures:** Small sample files (1-page PDF, simple .docx, .txt) in `src/test/resources/fixtures/`.

### H2 Compatibility Strategy

`TSVECTOR`, GIN index, PostgreSQL triggers, and the `@@` operator are PostgreSQL-specific and cannot run in H2. The strategy:

1. **H2 schema file:** Create `src/test/resources/schema-h2.sql` with an H2-compatible version of the `resumes` table (omit `search_vector` column, trigger, and GIN index; use `VARCHAR` instead of enum types).

2. **Search abstraction:** Define a `ResumeSearchRepository` interface with a `search(String query, Pageable pageable)` method. Two implementations:
   - `PostgresResumeSearchRepository` (`@Profile("!test")`) — uses native `@Query` with `search_vector @@ plainto_tsquery('simple', :query)`
   - `SimpleResumeSearchRepository` (`@Profile("test")`) — uses `LOWER(raw_text) LIKE LOWER('%' || :query || '%')`

3. **Testcontainers for FTS:** One dedicated test class (`ResumeSearchIntegrationTest`) uses `@Testcontainers` with PostgreSQL to verify the real full-text search, trigger, and GIN index work correctly. This test uses `@ActiveProfiles("integration")` (NOT `"test"`) so that `PostgresResumeSearchRepository` (`@Profile("!test")`) is loaded. Tagged `@Tag("slow")` and can be excluded from fast CI runs.

4. **Test profile config:** `application-test.yml` sets `spring.sql.init.schema-locations: classpath:schema-h2.sql` and `spring.jpa.hibernate.ddl-auto: create-drop` with Flyway disabled (matching existing pattern).

---

## 9. Async Event Contract

**`ResumeUploadedEvent`:**

```java
public class ResumeUploadedEvent extends ApplicationEvent {
    private final UUID resumeId;
    private final String rawText;
    private final String fileType;
}
```

Published after successful resume save (both success and degraded paths). `rawText` may be null if text extraction failed (status=UPLOADED) — consumers must handle this. No consumers in this module. The AI service integration module will add an `@EventListener` to trigger vectorization and structuring.

---

## 10. Permissions

Uses existing permissions from seed data:
- `resume:read` -- view and download resumes
- `resume:manage` -- upload, delete resumes, and update structured fields

The `PUT /structured` endpoint requires `resume:manage` permission. While primarily called by the AI service, using standard permission-based auth ensures the endpoint is secured at the application level regardless of network configuration.
