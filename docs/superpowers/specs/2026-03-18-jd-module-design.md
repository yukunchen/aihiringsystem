# JD (Job Description) Module Design

**Project**: AI Hiring Platform
**Module**: Job Description Management
**Date**: 2026-03-18

---

## 1. Overview

Job Description management module for the AI Hiring Platform. Provides CRUD operations for job postings with an enforced status lifecycle, department scoping, and keyword search. Serves as a prerequisite for the AI Matching Module (which pairs JDs with resumes).

**Scope**: Spring Boot side only. AI matching (vectorization, similarity search) is deferred to the AI Matching Service. This module stores the JD content and metadata that the matching service will consume.

**Tech stack**: Java 21, Spring Boot 3.3, Spring Data JPA, Flyway, PostgreSQL.

---

## 2. Architecture

### Package Structure

```
com.aihiring.job/
├── JobController.java
├── JobService.java
├── JobRepository.java
├── JobDescription.java          # Entity
├── JobStatus.java               # Enum: DRAFT, PUBLISHED, PAUSED, CLOSED
├── dto/
│   ├── CreateJobRequest.java
│   ├── UpdateJobRequest.java
│   ├── ChangeStatusRequest.java
│   ├── JobResponse.java
│   └── JobListResponse.java     # Truncated description for list view
```

### Key Design Decisions

1. **Enforced status lifecycle** -- Status changes go through a dedicated endpoint (`PUT /api/jobs/{id}/status`) with validated transitions. CRUD endpoints do not modify status. Transition rules are defined as a `Map<JobStatus, Set<JobStatus>>` in `JobService` -- simple, no state machine library.

2. **Department scoping as metadata** -- `department_id` FK tracks which department owns the JD. All JDs are visible to any user with `job:read` permission (shared pool, same as resumes). Department is for filtering, not access control.

3. **Delete restrictions** -- Hard delete only allowed on DRAFT and CLOSED JDs. Attempting to delete a PUBLISHED or PAUSED JD returns 400. This prevents accidentally removing active job postings.

4. **Skills as JSONB** -- Stored as a JSON array of strings, consistent with the resume module's structured fields. PostgreSQL JSONB operators enable future filtering.

5. **No full-text search** -- JD volume is low (tens/hundreds). Simple LIKE search on title is sufficient. Full-text search can be added later if needed.

6. **No new dependencies** -- Uses existing project infrastructure (JPA, validation, Flyway). No additional libraries required.

---

## 3. Data Model

### JobDescription Entity

`JobDescription extends BaseEntity` (inherits `id`, `createdAt`, `updatedAt` from `com.aihiring.common.entity.BaseEntity`).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| id | UUID | PK, auto-generated | Inherited from BaseEntity |
| title | String | Not null, max 200 | Job title |
| description | Text | Not null | Full job description |
| requirements | Text | Nullable | Job requirements |
| skills | JSONB | Nullable | `["Java", "Spring Boot"]` |
| education | String | Nullable, max 50 | e.g., "本科", "硕士" |
| experience | String | Nullable, max 50 | e.g., "3-5年" |
| salary_range | String | Nullable, max 100 | e.g., "15k-25k" |
| location | String | Nullable, max 100 | Work location |
| status | Enum | Not null, default DRAFT | DRAFT, PUBLISHED, PAUSED, CLOSED |
| department_id | UUID | FK -> departments, not null | Owning department |
| created_by | UUID | FK -> users, not null | Who created for audit |
| created_at | Timestamp | Auto-set | |
| updated_at | Timestamp | Auto-set | |

### Status Lifecycle

```
DRAFT -> PUBLISHED
PUBLISHED -> PAUSED
PUBLISHED -> CLOSED
PAUSED -> PUBLISHED
PAUSED -> CLOSED
```

Invalid transitions (e.g., DRAFT->CLOSED, CLOSED->anything) return 400.

---

## 4. API Endpoints

### Job Description (`/api/jobs`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/jobs` | `job:manage` | Create JD (status defaults to DRAFT) |
| GET | `/api/jobs` | `job:read` | List JDs (paginated, filterable) |
| GET | `/api/jobs/{id}` | `job:read` | Get JD detail |
| PUT | `/api/jobs/{id}` | `job:manage` | Update JD fields (not status) |
| DELETE | `/api/jobs/{id}` | `job:manage` | Delete JD (DRAFT/CLOSED only) |
| PUT | `/api/jobs/{id}/status` | `job:manage` | Change status (validated transitions) |

### Create Request

```json
{
  "title": "Senior Java Developer",
  "description": "We are looking for...",
  "requirements": "5+ years experience...",
  "skills": ["Java", "Spring Boot", "PostgreSQL"],
  "education": "本科",
  "experience": "3-5年",
  "salaryRange": "15k-25k",
  "location": "北京",
  "departmentId": "UUID"
}
```

**Validation:**
- `title`: required, max 200 characters
- `description`: required
- `departmentId`: required, must reference an existing department
- All other fields optional

### Update Request

Same fields as create, but all optional. Only provided fields are updated. `departmentId` can be changed. Status cannot be changed through this endpoint.

### Change Status Request

```json
{
  "status": "PUBLISHED"
}
```

Returns 400 with message if transition is invalid.

### List Query Parameters

- `page`, `size`, `sort` -- standard Spring Pageable
- `status` -- filter by job status
- `departmentId` -- filter by department
- `keyword` -- LIKE search on title (case-insensitive)

### Response Format

All responses wrapped in standard `ApiResponse`:

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

**JobResponse (detail):**

```json
{
  "id": "UUID",
  "title": "Senior Java Developer",
  "description": "full description text",
  "requirements": "requirements text",
  "skills": ["Java", "Spring Boot"],
  "education": "本科",
  "experience": "3-5年",
  "salaryRange": "15k-25k",
  "location": "北京",
  "status": "DRAFT",
  "department": { "id": "UUID", "name": "Engineering" },
  "createdBy": { "id": "UUID", "username": "admin" },
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

**JobListResponse**: Same as above but `description` truncated to first 200 chars. `requirements` omitted.

---

## 5. Database Migration

**`V4__job_description_table.sql`:**

```sql
CREATE TYPE job_status AS ENUM ('DRAFT', 'PUBLISHED', 'PAUSED', 'CLOSED');

CREATE TABLE job_descriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    requirements TEXT,
    skills JSONB,
    education VARCHAR(50),
    experience VARCHAR(50),
    salary_range VARCHAR(100),
    location VARCHAR(100),
    status job_status NOT NULL DEFAULT 'DRAFT',
    department_id UUID NOT NULL REFERENCES departments(id) ON DELETE RESTRICT,
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_job_descriptions_status ON job_descriptions(status);
CREATE INDEX idx_job_descriptions_department ON job_descriptions(department_id);
CREATE INDEX idx_job_descriptions_created_by ON job_descriptions(created_by);
CREATE INDEX idx_job_descriptions_created_at ON job_descriptions(created_at DESC);
CREATE INDEX idx_job_descriptions_title ON job_descriptions(title);
```

H2 test schema (`schema-h2.sql`) will add the equivalent table with VARCHAR instead of enum, CLOB instead of JSONB.

---

## 6. Error Handling

| Scenario | Response | Code |
|----------|----------|------|
| Missing required fields (title, description, departmentId) | Validation error details | 400 |
| Invalid status transition | "Invalid status transition from {current} to {target}" | 400 |
| Delete non-DRAFT/CLOSED JD | "Cannot delete job with status {status}. Only DRAFT and CLOSED jobs can be deleted" | 400 |
| Department not found | "Department not found" | 404 |
| JD not found | "Job description not found" | 404 |

Uses existing `BusinessException` (400) and `ResourceNotFoundException` (404) from `com.aihiring.common.exception`.

---

## 7. Testing Strategy

| Layer | What | Tools |
|-------|------|-------|
| Unit | JobService (create, update, delete, status transitions) | JUnit 5 + Mockito |
| Unit | Status transition validation (all valid/invalid combos) | JUnit 5 |
| Integration | JobController (CRUD, status, filters, auth) | SpringBootTest + MockMvc + H2 |

### H2 Compatibility

Same strategy as resume module:
- H2 schema in `schema-h2.sql` with VARCHAR instead of PostgreSQL enums, CLOB instead of JSONB
- `@Enumerated(EnumType.STRING)` for status
- Skills stored as TEXT/CLOB in H2, mapped as String in entity

---

## 8. Permissions

Uses existing permissions from seed data:
- `job:read` -- view job descriptions
- `job:manage` -- create, update, delete, change status
