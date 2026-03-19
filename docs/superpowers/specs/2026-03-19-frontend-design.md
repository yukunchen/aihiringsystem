# Frontend Design Spec

**Date:** 2026-03-19
**Product:** AI-Hiring Recruitment Platform
**Scope:** MVP frontend — auth, resume management, JD management, AI matching

---

## Overview

A desktop-first React SPA that gives HR teams a unified interface for uploading resumes, managing job descriptions, and running AI-powered JD-to-resume matching. The frontend communicates exclusively with the Spring Boot backend via REST APIs over JWT authentication.

---

## Tech Stack

| Concern | Choice |
|---------|--------|
| Framework | React 18 |
| Build tool | Vite |
| UI library | Ant Design 5 |
| Routing | React Router v6 |
| HTTP | Native `fetch` wrapped in a shared `request()` helper |
| State | React Context (auth only) — no global state library |
| Language | TypeScript |

---

## Project Structure

```
frontend/
├── index.html
├── vite.config.ts
├── tsconfig.json
├── package.json
└── src/
    ├── main.tsx                  # App bootstrap
    ├── App.tsx                   # Route definitions
    ├── api/                      # Domain service modules
    │   ├── request.ts            # Shared fetch wrapper (auth headers, 401 handling)
    │   ├── auth.ts               # login, refresh, logout
    │   ├── resumes.ts            # upload, list, get, download, delete
    │   ├── jobs.ts               # CRUD, status change
    │   ├── departments.ts        # list departments (for JD form select)
    │   └── match.ts              # POST /api/match
    ├── context/
    │   └── AuthContext.tsx       # token (in-memory), user info, login(), logout()
    ├── components/
    │   ├── AppLayout.tsx         # Left sidebar + content area shell
    │   └── ProtectedRoute.tsx    # Redirects to /login if unauthenticated
    └── pages/
        ├── login/
        │   └── LoginPage.tsx
        ├── resumes/
        │   ├── ResumeListPage.tsx
        │   └── ResumeUploadPage.tsx
        └── jobs/
            ├── JobListPage.tsx
            ├── JobCreatePage.tsx
            └── JobDetailPage.tsx
```

---

## Auth

### Token storage strategy

The backend `POST /api/auth/login` returns `{ accessToken, refreshToken, expiresIn }` (no user object). There is no HttpOnly cookie mechanism.

- **Access token**: stored in React state (in-memory) via `AuthContext`. Lost on page reload — restored via silent refresh on app init.
- **Refresh token**: stored in `localStorage`. Needed to survive page reloads since it must be sent as a JSON body field to `POST /api/auth/refresh`.

On app init (`AuthContext` mount): read refresh token from `localStorage`, call `POST /api/auth/refresh` with `{ refreshToken }`. On success, store the new access token in state and decode the JWT payload to populate `user`. On failure, clear `localStorage` and show `/login`.

User info is decoded from the JWT payload (standard claims: `sub` = userId UUID, `username` = username string, `roles` = string[]). No separate `/api/users/me` call required on every page load.

### AuthContext API
```ts
interface AuthContextValue {
  token: string | null;
  isInitializing: boolean;  // true while silent refresh is in progress on app mount
  user: { id: string; username: string; roles: string[] } | null;
  // JWT payload claims: sub=userId, username=username, roles=string[] (role names)
  // Decode with atob(token.split('.')[1]) — no library needed for reading claims
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}
```

`login()`: calls `POST /api/auth/login`, stores `refreshToken` in `localStorage`, stores `accessToken` in state, decodes JWT payload for `user`.

`logout()`: calls `POST /api/auth/logout` with body `{ refreshToken }` (read from `localStorage`), then clears both `localStorage` and state regardless of response.

### Request helper
`src/api/request.ts` exports a `request(path, options)` function that:
- Injects `Authorization: Bearer <token>` on every call
- On 401 response: clears `AuthContext` + `localStorage` and redirects to `/login`. **Exception**: the silent-refresh call during app init bypasses this handler — if it returns 401, the `AuthContext` init logic handles it directly (does not redirect in a loop).
- On 400 response: throws an error including the `data` field from the response body (which contains per-field validation messages) so form pages can display them
- On other non-2xx responses: throws an error with the `message` field from the response body

All domain modules (`auth.ts`, `resumes.ts`, etc.) use this helper exclusively.

---

## Routing

| Path | Component | Auth required |
|------|-----------|---------------|
| `/login` | `LoginPage` | No (redirects to `/jobs` if already authed) |
| `/resumes` | `ResumeListPage` | Yes |
| `/resumes/upload` | `ResumeUploadPage` | Yes |
| `/jobs` | `JobListPage` | Yes |
| `/jobs/new` | `JobCreatePage` | Yes |
| `/jobs/:id` | `JobDetailPage` | Yes |
| `*` | Redirect to `/jobs` | — |

`ProtectedRoute` wraps all authenticated routes. It reads `AuthContext`; if `isInitializing` is true (silent refresh in progress), renders a full-page spinner. Once init completes, if no token, redirects to `/login` (saving the original `location` in React Router `state.from` for post-login redirect).

---

## Layout

`AppLayout` wraps all authenticated pages. It renders:

- **Left sidebar** (Ant Design `Sider`, fixed width 220px): navigation menu with items:
  - Resumes (`/resumes`)
  - Jobs (`/jobs`)
- **Top bar**: product name left, logged-in username + logout button right
- **Content area**: `<Outlet />` renders the active page

---

## Pages

### LoginPage (`/login`)
- Ant Design `Form` centered on page
- Fields: username, password
- Submit calls `auth.login()`, then redirects to `state.from` (original location saved by `ProtectedRoute`) or `/jobs` if none
- Shows inline error on 401

---

### ResumeListPage (`/resumes`)
- Ant Design `Table` with server-side pagination
- Columns: filename, source, status (color-coded `Tag`), uploaded date, actions
- Actions per row: download (calls `GET /api/resumes/{id}/download`), delete (with confirm dialog calling `DELETE /api/resumes/{id}`)
- "Upload Resume" button (top-right) → navigates to `/resumes/upload`
- Empty state: prompt to upload first resume

---

### ResumeUploadPage (`/resumes/upload`)
- Ant Design `Upload.Dragger` component
- Accepted types: `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- On file select: shows filename + size preview
- Submit: `POST /api/resumes/upload?source=MANUAL` (multipart/form-data, `source` defaults to `MANUAL`)
- On success: success notification + redirect to `/resumes`
- On error: inline error message (from response `message` field)

---

### JobListPage (`/jobs`)
- Ant Design `Table` with server-side pagination
- Columns: title, department name, status (`Tag` with colors: draft=default, published=green, paused=orange, closed=red), created date, actions
- Actions per row: view (→ `/jobs/:id`), delete (with confirm dialog calling `DELETE /api/jobs/{id}`)
- "Create JD" button (top-right) → navigates to `/jobs/new`
- Empty state: prompt to create first JD

---

### JobCreatePage (`/jobs/new`)
- Ant Design `Form`
- Fields:
  - Title (text, required)
  - Department (`Select` dropdown, required — options loaded from `GET /api/departments`, displays `name`, submits `id` as `departmentId: UUID`)
  - Description (textarea, required)
  - Requirements (textarea, optional)
  - Skills (text, optional — free text, e.g. comma-separated)
  - Education (text, optional)
  - Experience (text, optional)
  - Salary Range (text, optional)
  - Location (text, optional)
- Submit: `POST /api/jobs` → on success, redirect to `/jobs/:id` of the new job
- On 400 validation error: show per-field messages from response `data` object
- Cancel: back to `/jobs`

---

### JobDetailPage (`/jobs/:id`)
Divided into two sections:

**Section 1 — JD Info**
- Displays: title, department name, status badge, description, requirements, skills, education, experience, salary range, location
- Status change: `Select` dropdown with valid next-state options — calls `PUT /api/jobs/{id}/status`. On 400/422 (invalid transition): show inline error from response `message` field.
- "Edit" button → inline form edit mode (same fields as JobCreatePage, saves via `PUT /api/jobs/{id}`)

**Section 2 — AI Matching**
- "Find Matching Resumes" button with a `top_k` input (number, default 10, max 50)
- On click: calls `POST /api/match` with `{ jobId, topK }`
- Shows Ant Design `Spin` loading indicator during request
- On success: results `Table` with columns:
  - Rank (#)
  - Resume ID (display as truncated UUID; resolving to filename requires individual `GET /api/resumes/{id}` calls — deferred to a future iteration to avoid N+1; show UUID for MVP)
  - Vector Score (0–1, two decimal places)
  - LLM Score (0–100, colored number: ≥80 green, 60–79 orange, <60 red)
  - Reasoning (truncated text, expandable via tooltip or expand row)
  - Highlights (Ant Design `Tag` list)
- On error (422 — JD not vectorized yet): warning alert "This JD has not been indexed yet. It will be ready shortly after creation."
- On error (503 — AI service down): error alert "AI matching service is currently unavailable."
- Empty results: "No matching resumes found."

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| 401 on any API call | Clear auth + localStorage, redirect to `/login` |
| 400 on form submit | Show per-field validation messages from response `data` object |
| 403 | Show Ant Design `Result` component: "You don't have permission to view this." |
| 404 | Show `Result`: "Not found." |
| 422 (match — JD not indexed) | Inline warning alert in match section |
| 422 (invalid job status transition) | Inline error below status selector |
| 503 (AI service down) | Inline error alert in match section |
| Network error | Ant Design `message.error()` toast notification |

---

## API Modules

### `api/request.ts`
```ts
export async function request<T>(path: string, options?: RequestInit): Promise<T>
```
Injects auth header. Handles 401 (clear + redirect), 400 (throws with `data` field for per-field errors), other non-2xx (throws with `message`). Silent-refresh call bypasses 401 handler.

### `api/auth.ts`
```ts
// Backend response shape: { code, message, data: { accessToken, refreshToken, expiresIn } }
login(username: string, password: string): Promise<{ accessToken: string; refreshToken: string }>
refresh(refreshToken: string): Promise<{ accessToken: string; refreshToken: string }>
logout(refreshToken: string): Promise<void>
```

### `api/resumes.ts`
```ts
listResumes(page: number, size: number): Promise<Page<ResumeListItem>>
uploadResume(file: File): Promise<ResumeDetail>
downloadResume(id: string): Promise<Blob>
deleteResume(id: string): Promise<void>
```

`ResumeListItem` maps `ResumeListResponse` from backend: `{ id, fileName, fileType, source, status, uploadedAt }`.

### `api/jobs.ts`
```ts
listJobs(page: number, size: number): Promise<Page<JobListItem>>
getJob(id: string): Promise<JobDetail>
createJob(data: CreateJobRequest): Promise<JobDetail>
updateJob(id: string, data: UpdateJobRequest): Promise<JobDetail>
changeJobStatus(id: string, status: string): Promise<JobDetail>  // PUT /api/jobs/{id}/status
deleteJob(id: string): Promise<void>
```

`CreateJobRequest`: `{ title, departmentId: string (UUID), description, requirements?, skills?, education?, experience?, salaryRange?, location? }`

### `api/departments.ts`
```ts
listDepartments(): Promise<Department[]>
// Department: { id: string, name: string }
```

### `api/match.ts`
```ts
match(jobId: string, topK: number): Promise<MatchResponse>
// MatchResponse: { jobId: string, results: MatchResultItem[] }
// MatchResultItem: { resumeId, vectorScore, llmScore, reasoning, highlights: string[] }
```

---

## Out of Scope (this iteration)

- Resume filename resolution in match results (N+1 problem — show UUID for MVP)
- User management (create/edit/delete users)
- Department management UI (departments loaded read-only for JD form)
- Role management
- Boss Zhipin integration
- Mobile responsiveness
- Dark mode
- Internationalization (i18n)
