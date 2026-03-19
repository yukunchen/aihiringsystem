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

### Token storage
Access token stored in React state (in-memory) via `AuthContext`. Not persisted to `localStorage` to avoid XSS exposure. On page refresh, the app silently calls `POST /api/auth/refresh`; if the refresh token cookie is valid, a new access token is issued and the user stays logged in. If refresh fails, the user is redirected to `/login`.

### AuthContext API
```ts
interface AuthContextValue {
  token: string | null;
  user: { id: string; username: string; roles: string[] } | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}
```

### Request helper
`src/api/request.ts` exports a `request(path, options)` function that:
- Injects `Authorization: Bearer <token>` on every call
- On 401 response: clears `AuthContext` and redirects to `/login`
- On other non-2xx responses: throws an error with the response body

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

`ProtectedRoute` wraps all authenticated routes. It reads `AuthContext`; if no token, redirects to `/login` (preserving the original `location` for post-login redirect).

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
- Submit calls `auth.login()`, then redirects to `/jobs` (or original location)
- Shows inline error on 401

---

### ResumeListPage (`/resumes`)
- Ant Design `Table` with server-side pagination
- Columns: filename, source, status (color-coded `Tag`), uploaded date, actions
- Actions per row: download (calls `/api/resumes/{id}/download`), delete (with confirm dialog)
- "Upload Resume" button (top-right) → navigates to `/resumes/upload`
- Empty state: prompt to upload first resume

---

### ResumeUploadPage (`/resumes/upload`)
- Ant Design `Upload.Dragger` component
- Accepted types: `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- On file select: shows filename + size preview
- Submit: `POST /api/resumes/upload` (multipart/form-data)
- On success: success notification + redirect to `/resumes`
- On error: inline error message

---

### JobListPage (`/jobs`)
- Ant Design `Table` with server-side pagination
- Columns: title, department, status (`Tag` with colors: draft=default, published=green, paused=orange, closed=red), created date, actions
- Actions per row: view (→ `/jobs/:id`)
- "Create JD" button (top-right) → navigates to `/jobs/new`
- Empty state: prompt to create first JD

---

### JobCreatePage (`/jobs/new`)
- Ant Design `Form`
- Fields:
  - Title (text, required)
  - Department (text, required)
  - Description (textarea, required)
  - Requirements (textarea, optional)
  - Skills (text, optional — comma-separated or JSON array)
- Submit: `POST /api/jobs` → on success, redirect to `/jobs/:id` of the new job
- Cancel: back to `/jobs`

---

### JobDetailPage (`/jobs/:id`)
Divided into two sections:

**Section 1 — JD Info**
- Displays: title, department, status badge, description, requirements, skills
- Status change: `Select` dropdown (draft → published → paused / closed) — calls `PUT /api/jobs/:id/status`
- "Edit" button → inline form edit mode (same fields as JobCreatePage, saves via `PUT /api/jobs/:id`)

**Section 2 — AI Matching**
- "Find Matching Resumes" button with a `top_k` input (number, default 10, max 50)
- On click: calls `POST /api/match` with `{ jobId, topK }`
- Shows Ant Design `Spin` loading indicator during request
- On success: results `Table` with columns:
  - Rank (#)
  - Resume (filename from resumeId)
  - Vector Score (0–1, shown as progress bar or decimal)
  - LLM Score (0–100, shown as colored number: ≥80 green, 60–79 orange, <60 red)
  - Reasoning (truncated text, expandable)
  - Highlights (tags)
- On error (422 — JD not vectorized yet): warning message "This JD has not been indexed yet. It will be ready shortly after creation."
- On error (503 — AI service down): error message "AI matching service is currently unavailable."
- Empty results: "No matching resumes found."

---

## Error Handling

| Scenario | Behavior |
|----------|----------|
| 401 on any API call | Clear auth, redirect to `/login` |
| 403 | Show Ant Design `Result` component: "You don't have permission to view this." |
| 404 | Show `Result`: "Not found." |
| 422 (match — JD not indexed) | Inline warning in match section |
| 503 (AI service down) | Inline error in match section |
| Network error | Ant Design `message.error()` notification |

---

## API Modules

### `api/request.ts`
```ts
export async function request<T>(path: string, options?: RequestInit): Promise<T>
```
Injects auth header, handles 401 redirect, throws on non-2xx.

### `api/auth.ts`
```ts
login(username: string, password: string): Promise<{ accessToken: string; user: User }>
refresh(): Promise<{ accessToken: string; user: User }>
logout(): Promise<void>
```

### `api/resumes.ts`
```ts
listResumes(page: number, size: number): Promise<Page<Resume>>
uploadResume(file: File): Promise<Resume>
downloadResume(id: string): Promise<Blob>
deleteResume(id: string): Promise<void>
```

### `api/jobs.ts`
```ts
listJobs(page: number, size: number): Promise<Page<Job>>
getJob(id: string): Promise<Job>
createJob(data: CreateJobRequest): Promise<Job>
updateJob(id: string, data: UpdateJobRequest): Promise<Job>
changeJobStatus(id: string, status: JobStatus): Promise<Job>
```

### `api/match.ts`
```ts
match(jobId: string, topK: number): Promise<MatchResponse>
```

---

## Out of Scope (this iteration)

- User management (create/edit/delete users)
- Department management
- Role management
- Boss Zhipin integration
- Mobile responsiveness
- Dark mode
- Internationalization (i18n)
