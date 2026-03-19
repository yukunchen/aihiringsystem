# Frontend MVP Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a desktop-first React SPA for HR teams to manage resumes, job descriptions, and run AI-powered matching.

**Architecture:** React 18 + TypeScript SPA scaffolded with Vite; Ant Design 5 for UI; React Router v6 for client-side routing; a thin `request()` fetch wrapper handles auth headers, 401 redirect, and error shaping; `AuthContext` owns the token lifecycle (access token in memory, refresh token in localStorage). All API calls go through a Vite dev proxy to the Spring Boot backend on `http://localhost:8080`.

**Tech Stack:** React 18, TypeScript 5, Vite 5, Ant Design 5, React Router v6, Vitest, React Testing Library

---

## File Structure

```
frontend/
├── index.html
├── vite.config.ts            # proxy /api → localhost:8080, vitest config
├── tsconfig.json
├── package.json
└── src/
    ├── main.tsx                         # React root mount
    ├── App.tsx                          # All <Route> definitions
    ├── setupTests.ts                    # RTL cleanup, fetch mock reset
    ├── api/
    │   ├── request.ts                   # Shared fetch wrapper
    │   ├── types.ts                     # Shared Page<T> pagination type
    │   ├── auth.ts                      # login, refresh, logout
    │   ├── resumes.ts                   # listResumes, uploadResume, downloadResume, deleteResume
    │   ├── jobs.ts                      # listJobs, getJob, createJob, updateJob, changeJobStatus, deleteJob
    │   ├── departments.ts               # listDepartments
    │   └── match.ts                     # match
    ├── context/
    │   └── AuthContext.tsx              # token, user, isInitializing, login(), logout()
    ├── components/
    │   ├── AppLayout.tsx                # Sider nav + top bar shell
    │   └── ProtectedRoute.tsx           # Spinner while init, redirect if unauthed
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

## Chunk 1: Scaffold + API Layer

### Task 1: Project Scaffold

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/setupTests.ts`

- [ ] **Step 1: Scaffold the Vite project**

```bash
cd /home/ubuntu/WS/ai-hiring
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
```

- [ ] **Step 2: Install dependencies**

```bash
npm install antd @ant-design/icons react-router-dom
npm install -D vitest @vitest/coverage-v8 @testing-library/react @testing-library/user-event @testing-library/jest-dom jsdom
```

- [ ] **Step 3: Replace `vite.config.ts`**

```ts
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
  },
});
```

- [ ] **Step 4: Create `src/setupTests.ts`**

```ts
import '@testing-library/jest-dom';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  vi.restoreAllMocks();
});
```

- [ ] **Step 5: Replace `src/main.tsx`**

```tsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
```

- [ ] **Step 6: Create placeholder `src/App.tsx`**

```tsx
export default function App() {
  return <div>AI Hiring</div>;
}
```

- [ ] **Step 7: Verify dev server starts**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: build completes with no TypeScript errors. (The dev server requires an interactive terminal; use the build as the automated proxy check.)

- [ ] **Step 8: Commit**

```bash
git add frontend/
git commit -m "feat(frontend): scaffold Vite React TS project with Ant Design and Vitest"
```

---

### Task 2: API Request Helper

**Files:**
- Create: `frontend/src/api/request.ts`
- Test: `frontend/src/api/request.test.ts`

The `request()` helper:
- Injects `Authorization: Bearer <token>` if a token is set
- On 401: calls the registered unauthorized handler, then throws
- On 400: throws an error with a `data` property (per-field validation map from response body)
- On other non-2xx: throws an error with the `message` from response body
- On 2xx: returns `response.data` (unwraps `{ code, message, data }` envelope)
- Silent-refresh path: pass `{ skipAuthHandler: true }` to bypass the 401 handler

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/api/request.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { request, setAuthToken, setUnauthorizedHandler } from './request';

function mockFetch(status: number, body: unknown) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
    ok: status >= 200 && status < 300,
    status,
    json: () => Promise.resolve(body),
  } as Response);
}

beforeEach(() => {
  setAuthToken(null);
  setUnauthorizedHandler(null);
});

describe('request()', () => {
  it('returns data on 2xx response', async () => {
    mockFetch(200, { code: 200, message: 'ok', data: { id: '1' } });
    const result = await request<{ id: string }>('/api/test');
    expect(result).toEqual({ id: '1' });
  });

  it('injects Authorization header when token is set', async () => {
    setAuthToken('my-token');
    const spy = mockFetch(200, { code: 200, message: 'ok', data: {} });
    await request('/api/test');
    const headers = spy.mock.calls[0][1]?.headers as Record<string, string>;
    expect(headers['Authorization']).toBe('Bearer my-token');
  });

  it('does not inject Authorization header when token is null', async () => {
    const spy = mockFetch(200, { code: 200, message: 'ok', data: {} });
    await request('/api/test');
    const headers = (spy.mock.calls[0][1]?.headers ?? {}) as Record<string, string>;
    expect(headers['Authorization']).toBeUndefined();
  });

  it('calls unauthorized handler on 401 and throws', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetch(401, { code: 401, message: 'Unauthorized', data: null });
    await expect(request('/api/test')).rejects.toThrow();
    expect(handler).toHaveBeenCalled();
  });

  it('skips unauthorized handler on 401 when skipAuthHandler is true', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetch(401, { code: 401, message: 'Unauthorized', data: null });
    await expect(request('/api/test', {}, { skipAuthHandler: true })).rejects.toThrow();
    expect(handler).not.toHaveBeenCalled();
  });

  it('throws error with data property on 400', async () => {
    mockFetch(400, {
      code: 400,
      message: 'Validation failed',
      data: { title: 'Title is required' },
    });
    const error = await request('/api/test').catch((e) => e);
    expect(error.data).toEqual({ title: 'Title is required' });
  });

  it('throws error with message on other non-2xx', async () => {
    mockFetch(500, { code: 500, message: 'Internal server error', data: null });
    await expect(request('/api/test')).rejects.toThrow('Internal server error');
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/api/request.test.ts
```

Expected: FAIL — `request` not found.

- [ ] **Step 3: Implement `src/api/request.ts`**

```ts
let _token: string | null = null;
let _onUnauthorized: (() => void) | null = null;

export function setAuthToken(token: string | null): void {
  _token = token;
}

export function setUnauthorizedHandler(fn: (() => void) | null): void {
  _onUnauthorized = fn;
}

export function getToken(): string | null {
  return _token;
}

interface RequestOptions {
  skipAuthHandler?: boolean;
}

export async function request<T>(
  path: string,
  init: RequestInit = {},
  options: RequestOptions = {}
): Promise<T> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init.headers as Record<string, string>),
  };

  if (_token) {
    headers['Authorization'] = `Bearer ${_token}`;
  }

  const response = await fetch(path, { ...init, headers });
  const body = await response.json();

  if (response.ok) {
    return body.data as T;
  }

  if (response.status === 401) {
    if (!options.skipAuthHandler && _onUnauthorized) {
      _onUnauthorized();
    }
    const err = new Error(body.message ?? 'Unauthorized');
    throw err;
  }

  if (response.status === 400) {
    const err: Error & { data?: unknown } = new Error(body.message ?? 'Bad request');
    err.data = body.data;
    throw err;
  }

  throw new Error(body.message ?? `HTTP ${response.status}`);
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/api/request.test.ts
```

Expected: PASS — 7 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/request.ts frontend/src/api/request.test.ts
git commit -m "feat(frontend): add request() helper with auth header, 401 handler, and error shaping"
```

---

### Task 3: Auth API Module

**Files:**
- Create: `frontend/src/api/auth.ts`
- Test: `frontend/src/api/auth.test.ts`

Backend response for login/refresh: `{ code, message, data: { accessToken, refreshToken, expiresIn } }`

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/api/auth.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => {
  vi.restoreAllMocks();
});

describe('auth API', () => {
  it('login() calls POST /api/auth/login and returns tokens', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({
      accessToken: 'access',
      refreshToken: 'refresh',
      expiresIn: 7200,
    });

    const { login } = await import('./auth');
    const result = await login('admin', 'password');

    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/auth/login',
      expect.objectContaining({ method: 'POST' })
    );
    expect(result).toEqual({ accessToken: 'access', refreshToken: 'refresh' });
  });

  it('refresh() calls POST /api/auth/refresh with refreshToken in body', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({
      accessToken: 'new-access',
      refreshToken: 'new-refresh',
      expiresIn: 7200,
    });

    const { refresh } = await import('./auth');
    await refresh('old-refresh');

    const [, init] = (requestModule.request as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(JSON.parse(init.body)).toMatchObject({ refreshToken: 'old-refresh' });
  });

  it('logout() calls POST /api/auth/logout with refreshToken in body', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(undefined);

    const { logout } = await import('./auth');
    await logout('my-refresh');

    const [path, init] = (requestModule.request as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(path).toBe('/api/auth/logout');
    expect(JSON.parse(init.body)).toMatchObject({ refreshToken: 'my-refresh' });
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/api/auth.test.ts
```

Expected: FAIL — `auth` not found.

- [ ] **Step 3: Implement `src/api/auth.ts`**

```ts
import { request } from './request';

interface TokenData {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export async function login(
  username: string,
  password: string
): Promise<{ accessToken: string; refreshToken: string }> {
  const data = await request<TokenData>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
  return { accessToken: data.accessToken, refreshToken: data.refreshToken };
}

export async function refresh(
  refreshToken: string
): Promise<{ accessToken: string; refreshToken: string }> {
  const data = await request<TokenData>(
    '/api/auth/refresh',
    {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
    },
    { skipAuthHandler: true }
  );
  return { accessToken: data.accessToken, refreshToken: data.refreshToken };
}

export async function logout(refreshToken: string): Promise<void> {
  await request<void>('/api/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken }),
  });
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/api/auth.test.ts
```

Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/api/auth.ts frontend/src/api/auth.test.ts
git commit -m "feat(frontend): add auth API module (login, refresh, logout)"
```

---

### Task 4: Domain API Modules

**Files:**
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/api/resumes.ts`
- Create: `frontend/src/api/jobs.ts`
- Create: `frontend/src/api/departments.ts`
- Create: `frontend/src/api/match.ts`
- Test: `frontend/src/api/resumes.test.ts`
- Test: `frontend/src/api/jobs.test.ts`
- Test: `frontend/src/api/departments.test.ts`
- Test: `frontend/src/api/match.test.ts`

**Types to define:**

`types.ts`: shared `Page<T>` — imported by both `resumes.ts` and `jobs.ts`
`resumes.ts`: `ResumeListItem { id, fileName, fileType, source, status, uploadedAt }`
`jobs.ts`: `JobListItem { id, title, departmentId, departmentName, status, createdAt }`, `JobDetail { ...JobListItem, description, requirements?, skills?, education?, experience?, salaryRange?, location? }`
`departments.ts`: `Department { id, name }`
`match.ts`: `MatchResultItem { resumeId, vectorScore, llmScore, reasoning, highlights: string[] }`, `MatchResponse { jobId, results: MatchResultItem[] }`

- [ ] **Step 1: Write failing tests for all four modules**

Create `frontend/src/api/resumes.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('resumes API', () => {
  it('listResumes() calls GET /api/resumes with pagination', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
    const { listResumes } = await import('./resumes');
    await listResumes(0, 10);
    expect(requestModule.request).toHaveBeenCalledWith('/api/resumes?page=0&size=10');
  });

  it('deleteResume() calls DELETE /api/resumes/:id', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(undefined);
    const { deleteResume } = await import('./resumes');
    await deleteResume('abc-123');
    expect(requestModule.request).toHaveBeenCalledWith('/api/resumes/abc-123', { method: 'DELETE' });
  });

  it('downloadResume() calls GET /api/resumes/:id/download', async () => {
    const mockBlob = new Blob(['pdf'], { type: 'application/pdf' });
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      blob: () => Promise.resolve(mockBlob),
    } as Response);
    const { downloadResume } = await import('./resumes');
    const result = await downloadResume('abc-123');
    expect(result).toBe(mockBlob);
  });

  it('uploadResume() POSTs multipart and returns resume on success', async () => {
    const mockResume = { id: 'r1', fileName: 'cv.pdf', fileType: 'PDF', source: 'MANUAL', status: 'UPLOADED', uploadedAt: '2026-03-01T00:00:00Z' };
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: () => Promise.resolve({ code: 200, message: 'ok', data: mockResume }),
    } as Response);
    const { uploadResume } = await import('./resumes');
    const file = new File(['content'], 'cv.pdf', { type: 'application/pdf' });
    const result = await uploadResume(file);
    expect(result).toEqual(mockResume);
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toBe('/api/resumes/upload?source=MANUAL');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
  });
});
```

Create `frontend/src/api/jobs.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('jobs API', () => {
  it('listJobs() calls GET /api/jobs with pagination', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 10 });
    const { listJobs } = await import('./jobs');
    await listJobs(0, 10);
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs?page=0&size=10');
  });

  it('createJob() calls POST /api/jobs', async () => {
    const mockJob = { id: '1', title: 'SWE', departmentId: 'dept-1', departmentName: 'Eng', status: 'DRAFT', createdAt: '' };
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(mockJob);
    const { createJob } = await import('./jobs');
    const result = await createJob({ title: 'SWE', departmentId: 'dept-1', description: 'desc' });
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs', expect.objectContaining({ method: 'POST' }));
    expect(result.title).toBe('SWE');
  });

  it('changeJobStatus() calls PUT /api/jobs/:id/status', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({});
    const { changeJobStatus } = await import('./jobs');
    await changeJobStatus('job-1', 'PUBLISHED');
    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/jobs/job-1/status',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ status: 'PUBLISHED' }) })
    );
  });

  it('getJob() calls GET /api/jobs/:id', async () => {
    const mockJob = { id: 'j1', title: 'SWE', departmentId: 'd1', departmentName: 'Eng', status: 'DRAFT', createdAt: '', description: 'desc' };
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(mockJob);
    const { getJob } = await import('./jobs');
    const result = await getJob('j1');
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs/j1');
    expect(result.title).toBe('SWE');
  });

  it('updateJob() calls PUT /api/jobs/:id', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce({});
    const { updateJob } = await import('./jobs');
    await updateJob('j1', { title: 'Updated' });
    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/jobs/j1',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ title: 'Updated' }) })
    );
  });

  it('deleteJob() calls DELETE /api/jobs/:id', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(undefined);
    const { deleteJob } = await import('./jobs');
    await deleteJob('job-1');
    expect(requestModule.request).toHaveBeenCalledWith('/api/jobs/job-1', { method: 'DELETE' });
  });
});
```

Create `frontend/src/api/departments.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('departments API', () => {
  it('listDepartments() calls GET /api/departments', async () => {
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce([{ id: '1', name: 'Engineering' }]);
    const { listDepartments } = await import('./departments');
    const result = await listDepartments();
    expect(requestModule.request).toHaveBeenCalledWith('/api/departments');
    expect(result[0].name).toBe('Engineering');
  });
});
```

Create `frontend/src/api/match.test.ts`:

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as requestModule from './request';

beforeEach(() => { vi.restoreAllMocks(); });

describe('match API', () => {
  it('match() calls POST /api/match with jobId and topK', async () => {
    const mockResponse = { jobId: 'job-1', results: [] };
    vi.spyOn(requestModule, 'request').mockResolvedValueOnce(mockResponse);
    const { match } = await import('./match');
    const result = await match('job-1', 10);
    expect(requestModule.request).toHaveBeenCalledWith(
      '/api/match',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ jobId: 'job-1', topK: 10 }) })
    );
    expect(result.jobId).toBe('job-1');
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/api/resumes.test.ts src/api/jobs.test.ts src/api/departments.test.ts src/api/match.test.ts
```

Expected: FAIL — modules not found.

- [ ] **Step 3: Create `src/api/types.ts`**

```ts
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
```

- [ ] **Step 4: Implement `src/api/resumes.ts`**

```ts
import { getToken, request } from './request';
import type { Page } from './types';

export type { Page };

export interface ResumeListItem {
  id: string;
  fileName: string;
  fileType: string;
  source: string;
  status: string;
  uploadedAt: string;
}

export async function listResumes(page: number, size: number): Promise<Page<ResumeListItem>> {
  return request<Page<ResumeListItem>>(`/api/resumes?page=${page}&size=${size}`);
}

export async function uploadResume(file: File): Promise<ResumeListItem> {
  const formData = new FormData();
  formData.append('file', file);
  // Upload uses multipart — skip Content-Type header (browser sets it with boundary)
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch('/api/resumes/upload?source=MANUAL', {
    method: 'POST',
    headers,
    body: formData,
  });
  const body = await response.json();
  if (response.ok) return body.data;
  if (response.status === 400) {
    const err: Error & { data?: unknown } = new Error(body.message);
    err.data = body.data;
    throw err;
  }
  throw new Error(body.message ?? `HTTP ${response.status}`);
}

export async function downloadResume(id: string): Promise<Blob> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch(`/api/resumes/${id}/download`, { headers });
  if (!response.ok) throw new Error('Download failed');
  return response.blob();
}

export async function deleteResume(id: string): Promise<void> {
  return request<void>(`/api/resumes/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 5: Implement `src/api/jobs.ts`**

```ts
import { request } from './request';
import type { Page } from './types';

export interface JobListItem {
  id: string;
  title: string;
  departmentId: string;
  departmentName: string;
  status: string;
  createdAt: string;
}

export interface JobDetail extends JobListItem {
  description: string;
  requirements?: string;
  skills?: string;
  education?: string;
  experience?: string;
  salaryRange?: string;
  location?: string;
}

export interface CreateJobRequest {
  title: string;
  departmentId: string;
  description: string;
  requirements?: string;
  skills?: string;
  education?: string;
  experience?: string;
  salaryRange?: string;
  location?: string;
}

export type UpdateJobRequest = Partial<CreateJobRequest>;

export async function listJobs(page: number, size: number): Promise<Page<JobListItem>> {
  return request<Page<JobListItem>>(`/api/jobs?page=${page}&size=${size}`);
}

export async function getJob(id: string): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${id}`);
}

export async function createJob(data: CreateJobRequest): Promise<JobDetail> {
  return request<JobDetail>('/api/jobs', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateJob(id: string, data: UpdateJobRequest): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function changeJobStatus(id: string, status: string): Promise<JobDetail> {
  return request<JobDetail>(`/api/jobs/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status }),
  });
}

export async function deleteJob(id: string): Promise<void> {
  return request<void>(`/api/jobs/${id}`, { method: 'DELETE' });
}
```

- [ ] **Step 6: Implement `src/api/departments.ts`**

```ts
import { request } from './request';

export interface Department {
  id: string;
  name: string;
}

export async function listDepartments(): Promise<Department[]> {
  return request<Department[]>('/api/departments');
}
```

- [ ] **Step 7: Implement `src/api/match.ts`**

```ts
import { request } from './request';

export interface MatchResultItem {
  resumeId: string;
  vectorScore: number;
  llmScore: number;
  reasoning: string;
  highlights: string[];
}

export interface MatchResponse {
  jobId: string;
  results: MatchResultItem[];
}

export async function match(jobId: string, topK: number): Promise<MatchResponse> {
  return request<MatchResponse>('/api/match', {
    method: 'POST',
    body: JSON.stringify({ jobId, topK }),
  });
}
```

- [ ] **Step 8: Run all API module tests**

```bash
cd frontend && npm run test -- --run src/api/resumes.test.ts src/api/jobs.test.ts src/api/departments.test.ts src/api/match.test.ts
```

Expected: PASS — 12 tests total (resumes: 4, jobs: 6, departments: 1, match: 1).

- [ ] **Step 9: Commit**

```bash
git add frontend/src/api/
git commit -m "feat(frontend): add domain API modules (resumes, jobs, departments, match)"
```

---

## Chunk 2: Auth Context + Routing + Layout

### Task 5: AuthContext

**Files:**
- Create: `frontend/src/context/AuthContext.tsx`
- Test: `frontend/src/context/AuthContext.test.tsx`

JWT decode: `JSON.parse(atob(token.split('.')[1]))`. Claims: `sub` = userId UUID, `username` = username string, `roles` = string[].

On mount: read `refreshToken` from `localStorage`, call `refresh()` with `{ skipAuthHandler: true }`, store access token in state + decode user. On failure: clear localStorage, leave `token = null`.

- [ ] **Step 1: Write failing tests**

Create `frontend/src/context/AuthContext.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// Mock API modules
vi.mock('../api/auth', () => ({
  login: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
}));
vi.mock('../api/request', () => ({
  setAuthToken: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}));

import * as authApi from '../api/auth';
import * as requestModule from '../api/request';
import { AuthProvider, useAuth } from './AuthContext';

// Helper: create a fake JWT with payload
function makeJwt(payload: object): string {
  const encoded = btoa(JSON.stringify(payload));
  return `header.${encoded}.sig`;
}

const mockAccessToken = makeJwt({ sub: 'user-1', username: 'alice', roles: ['ROLE_HR'] });
const mockRefreshToken = 'refresh-abc';

function TestConsumer() {
  const { token, user, isInitializing, login, logout } = useAuth();
  if (isInitializing) return <div>Loading...</div>;
  if (!token) return <button onClick={() => login('alice', 'pass')}>Login</button>;
  return (
    <div>
      <span>Hello {user?.username}</span>
      <button onClick={() => logout()}>Logout</button>
    </div>
  );
}

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});

describe('AuthContext', () => {
  it('shows loading then login button when no refresh token in localStorage', async () => {
    render(<AuthProvider><TestConsumer /></AuthProvider>);
    expect(screen.getByText('Loading...')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Login')).toBeInTheDocument());
  });

  it('restores session on mount when refresh token exists in localStorage', async () => {
    localStorage.setItem('refreshToken', mockRefreshToken);
    vi.mocked(authApi.refresh).mockResolvedValueOnce({
      accessToken: mockAccessToken,
      refreshToken: mockRefreshToken,
    });

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => expect(screen.getByText('Hello alice')).toBeInTheDocument());
    expect(authApi.refresh).toHaveBeenCalledWith(mockRefreshToken);
    expect(requestModule.setAuthToken).toHaveBeenCalledWith(mockAccessToken);
  });

  it('clears state when refresh fails on mount', async () => {
    localStorage.setItem('refreshToken', mockRefreshToken);
    vi.mocked(authApi.refresh).mockRejectedValueOnce(new Error('Unauthorized'));

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => expect(screen.getByText('Login')).toBeInTheDocument());
    expect(localStorage.getItem('refreshToken')).toBeNull();
  });

  it('login() stores tokens and decodes user', async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({
      accessToken: mockAccessToken,
      refreshToken: mockRefreshToken,
    });

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => screen.getByText('Login'));
    await userEvent.click(screen.getByText('Login'));

    await waitFor(() => expect(screen.getByText('Hello alice')).toBeInTheDocument());
    expect(localStorage.getItem('refreshToken')).toBe(mockRefreshToken);
    expect(requestModule.setAuthToken).toHaveBeenCalledWith(mockAccessToken);
  });

  it('logout() clears token and localStorage', async () => {
    localStorage.setItem('refreshToken', mockRefreshToken);
    vi.mocked(authApi.refresh).mockResolvedValueOnce({
      accessToken: mockAccessToken,
      refreshToken: mockRefreshToken,
    });
    vi.mocked(authApi.logout).mockResolvedValueOnce(undefined);

    render(<AuthProvider><TestConsumer /></AuthProvider>);
    await waitFor(() => screen.getByText('Hello alice'));
    await userEvent.click(screen.getByText('Logout'));

    await waitFor(() => expect(screen.getByText('Login')).toBeInTheDocument());
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(requestModule.setAuthToken).toHaveBeenCalledWith(null);
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/context/AuthContext.test.tsx
```

Expected: FAIL — `AuthContext` not found.

- [ ] **Step 3: Implement `src/context/AuthContext.tsx`**

```tsx
import React, { createContext, useContext, useEffect, useRef, useState } from 'react';
import * as authApi from '../api/auth';
import { setAuthToken, setUnauthorizedHandler } from '../api/request';

interface User {
  id: string;
  username: string;
  roles: string[];
}

interface AuthContextValue {
  token: string | null;
  isInitializing: boolean;
  user: User | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

function decodeUser(token: string): User {
  const payload = JSON.parse(atob(token.split('.')[1]));
  return { id: payload.sub, username: payload.username, roles: payload.roles ?? [] };
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<User | null>(null);
  const [isInitializing, setIsInitializing] = useState(true);
  const initialized = useRef(false);

  useEffect(() => {
    if (initialized.current) return;
    initialized.current = true;

    setUnauthorizedHandler(() => {
      setToken(null);
      setUser(null);
      setAuthToken(null);
      localStorage.removeItem('refreshToken');
    });

    const storedRefresh = localStorage.getItem('refreshToken');
    if (!storedRefresh) {
      setIsInitializing(false);
      return;
    }

    authApi
      .refresh(storedRefresh)
      .then(({ accessToken, refreshToken }) => {
        setAuthToken(accessToken);
        setToken(accessToken);
        setUser(decodeUser(accessToken));
        localStorage.setItem('refreshToken', refreshToken);
      })
      .catch(() => {
        localStorage.removeItem('refreshToken');
      })
      .finally(() => {
        setIsInitializing(false);
      });
  }, []);

  const login = async (username: string, password: string) => {
    const { accessToken, refreshToken } = await authApi.login(username, password);
    setAuthToken(accessToken);
    setToken(accessToken);
    setUser(decodeUser(accessToken));
    localStorage.setItem('refreshToken', refreshToken);
  };

  const logout = async () => {
    const refreshToken = localStorage.getItem('refreshToken') ?? '';
    try {
      await authApi.logout(refreshToken);
    } finally {
      setAuthToken(null);
      setToken(null);
      setUser(null);
      localStorage.removeItem('refreshToken');
    }
  };

  return (
    <AuthContext.Provider value={{ token, isInitializing, user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/context/AuthContext.test.tsx
```

Expected: PASS — 5 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/context/AuthContext.tsx frontend/src/context/AuthContext.test.tsx
git commit -m "feat(frontend): add AuthContext with silent refresh, login, logout"
```

---

### Task 6: App Routing + ProtectedRoute + AppLayout

**Files:**
- Create: `frontend/src/components/ProtectedRoute.tsx`
- Create: `frontend/src/components/AppLayout.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/main.tsx`
- Test: `frontend/src/components/ProtectedRoute.test.tsx`

- [ ] **Step 1: Write failing tests for ProtectedRoute**

Create `frontend/src/components/ProtectedRoute.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '../context/AuthContext';
import ProtectedRoute from './ProtectedRoute';

describe('ProtectedRoute', () => {
  it('shows spinner while initializing', () => {
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: true, user: null, login: vi.fn(), logout: vi.fn() });
    render(
      <MemoryRouter initialEntries={['/jobs']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/jobs" element={<div>Jobs Page</div>} />
          </Route>
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.queryByText('Jobs Page')).not.toBeInTheDocument();
    expect(screen.queryByText('Login')).not.toBeInTheDocument();
  });

  it('redirects to /login when not authenticated', () => {
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() });
    render(
      <MemoryRouter initialEntries={['/jobs']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/jobs" element={<div>Jobs Page</div>} />
          </Route>
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('Login')).toBeInTheDocument();
    expect(screen.queryByText('Jobs Page')).not.toBeInTheDocument();
  });

  it('renders children when authenticated', () => {
    vi.mocked(useAuth).mockReturnValue({
      token: 'tok',
      isInitializing: false,
      user: { id: '1', username: 'alice', roles: [] },
      login: vi.fn(),
      logout: vi.fn(),
    });
    render(
      <MemoryRouter initialEntries={['/jobs']}>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/jobs" element={<div>Jobs Page</div>} />
          </Route>
          <Route path="/login" element={<div>Login</div>} />
        </Routes>
      </MemoryRouter>
    );
    expect(screen.getByText('Jobs Page')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/components/ProtectedRoute.test.tsx
```

Expected: FAIL — `ProtectedRoute` not found.

- [ ] **Step 3: Implement `src/components/ProtectedRoute.tsx`**

```tsx
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from '../context/AuthContext';

export default function ProtectedRoute() {
  const { token, isInitializing } = useAuth();
  const location = useLocation();

  if (isInitializing) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return <Outlet />;
}
```

- [ ] **Step 4: Implement `src/components/AppLayout.tsx`**

```tsx
import { Layout, Menu, Button, Typography, Space } from 'antd';
import { FileTextOutlined, SolutionOutlined } from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const { Sider, Header, Content } = Layout;

export default function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const selectedKey = location.pathname.startsWith('/resumes') ? 'resumes' : 'jobs';

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={220} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
        <div style={{ padding: '16px', fontWeight: 700, fontSize: 16 }}>AI Hiring</div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={[
            { key: 'resumes', icon: <FileTextOutlined />, label: 'Resumes', onClick: () => navigate('/resumes') },
            { key: 'jobs', icon: <SolutionOutlined />, label: 'Jobs', onClick: () => navigate('/jobs') },
          ]}
        />
      </Sider>
      <Layout>
        <Header style={{ background: '#fff', borderBottom: '1px solid #f0f0f0', padding: '0 24px', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography.Text strong>AI Hiring Platform</Typography.Text>
          <Space>
            <Typography.Text type="secondary">{user?.username}</Typography.Text>
            <Button onClick={handleLogout}>Logout</Button>
          </Space>
        </Header>
        <Content style={{ padding: 24 }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
```

- [ ] **Step 5: Wire up `src/App.tsx`**

```tsx
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import AppLayout from './components/AppLayout';
import LoginPage from './pages/login/LoginPage';
import ResumeListPage from './pages/resumes/ResumeListPage';
import ResumeUploadPage from './pages/resumes/ResumeUploadPage';
import JobListPage from './pages/jobs/JobListPage';
import JobCreatePage from './pages/jobs/JobCreatePage';
import JobDetailPage from './pages/jobs/JobDetailPage';

export default function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="/resumes" element={<ResumeListPage />} />
            <Route path="/resumes/upload" element={<ResumeUploadPage />} />
            <Route path="/jobs" element={<JobListPage />} />
            <Route path="/jobs/new" element={<JobCreatePage />} />
            <Route path="/jobs/:id" element={<JobDetailPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/jobs" replace />} />
      </Routes>
    </AuthProvider>
  );
}
```

Note: This step will fail to compile until all page components exist. Create placeholder stubs for each page now (empty components that return `<div>Page name</div>`), then fill in real implementations in Tasks 7–11.

- [ ] **Step 6a: Create `src/pages/login/LoginPage.tsx`**

```tsx
export default function LoginPage() { return <div>Login</div>; }
```

- [ ] **Step 6b: Create `src/pages/resumes/ResumeListPage.tsx`**

```tsx
export default function ResumeListPage() { return <div>Resume List</div>; }
```

- [ ] **Step 6c: Create `src/pages/resumes/ResumeUploadPage.tsx`**

```tsx
export default function ResumeUploadPage() { return <div>Resume Upload</div>; }
```

- [ ] **Step 6d: Create `src/pages/jobs/JobListPage.tsx`**

```tsx
export default function JobListPage() { return <div>Job List</div>; }
```

- [ ] **Step 6e: Create `src/pages/jobs/JobCreatePage.tsx`**

```tsx
export default function JobCreatePage() { return <div>Job Create</div>; }
```

- [ ] **Step 6f: Create `src/pages/jobs/JobDetailPage.tsx`**

```tsx
export default function JobDetailPage() { return <div>Job Detail</div>; }
```

- [ ] **Step 7: Run ProtectedRoute tests**

```bash
cd frontend && npm run test -- --run src/components/ProtectedRoute.test.tsx
```

Expected: PASS — 3 tests.

- [ ] **Step 8: Verify dev server builds without errors**

```bash
cd frontend && npm run build 2>&1 | tail -5
```

Expected: build completes with no TypeScript errors.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/
git commit -m "feat(frontend): add ProtectedRoute, AppLayout, App routing, and page stubs"
```

---

## Chunk 3: Login + Resume Pages

### Task 7: LoginPage

**Files:**
- Modify: `frontend/src/pages/login/LoginPage.tsx`
- Test: `frontend/src/pages/login/LoginPage.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `frontend/src/pages/login/LoginPage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';

vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '../../context/AuthContext';
import LoginPage from './LoginPage';

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useAuth).mockReturnValue({
    token: null, isInitializing: false, user: null,
    login: vi.fn(), logout: vi.fn(),
  });
});

describe('LoginPage', () => {
  it('renders username and password fields', () => {
    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    expect(screen.getByPlaceholderText(/username/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/password/i)).toBeInTheDocument();
  });

  it('calls login() and redirects to /jobs on success', async () => {
    const mockLogin = vi.fn().mockResolvedValueOnce(undefined);
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: mockLogin, logout: vi.fn() });

    render(<MemoryRouter initialEntries={[{ pathname: '/login', state: {} }]}><LoginPage /></MemoryRouter>);
    await userEvent.type(screen.getByPlaceholderText(/username/i), 'alice');
    await userEvent.type(screen.getByPlaceholderText(/password/i), 'secret');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(mockLogin).toHaveBeenCalledWith('alice', 'secret'));
    expect(mockNavigate).toHaveBeenCalledWith('/jobs', expect.anything());
  });

  it('shows error message on login failure (401)', async () => {
    const mockLogin = vi.fn().mockRejectedValueOnce(new Error('Invalid credentials'));
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: mockLogin, logout: vi.fn() });

    render(<MemoryRouter><LoginPage /></MemoryRouter>);
    await userEvent.type(screen.getByPlaceholderText(/username/i), 'alice');
    await userEvent.type(screen.getByPlaceholderText(/password/i), 'wrong');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(screen.getByText('Invalid credentials')).toBeInTheDocument());
  });

  it('redirects to state.from after successful login', async () => {
    const mockLogin = vi.fn().mockResolvedValueOnce(undefined);
    vi.mocked(useAuth).mockReturnValue({ token: null, isInitializing: false, user: null, login: mockLogin, logout: vi.fn() });

    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: { pathname: '/resumes' } } }]}>
        <LoginPage />
      </MemoryRouter>
    );
    await userEvent.type(screen.getByPlaceholderText(/username/i), 'alice');
    await userEvent.type(screen.getByPlaceholderText(/password/i), 'pass');
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/resumes', expect.anything()));
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/pages/login/LoginPage.test.tsx
```

Expected: FAIL — placeholder returns `<div>Login</div>`.

- [ ] **Step 3: Implement `src/pages/login/LoginPage.tsx`**

```tsx
import { useState } from 'react';
import { Form, Input, Button, Card, Alert } from 'antd';
import { useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const from = (location.state as { from?: { pathname: string } })?.from?.pathname ?? '/jobs';

  const onFinish = async (values: { username: string; password: string }) => {
    setError(null);
    setLoading(true);
    try {
      await login(values.username, values.password);
      navigate(from, { replace: true });
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f5f5f5' }}>
      <Card style={{ width: 360 }}>
        <h2 style={{ textAlign: 'center', marginBottom: 24 }}>AI Hiring</h2>
        {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="username" rules={[{ required: true }]}>
            <Input placeholder="Username" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true }]}>
            <Input.Password placeholder="Password" size="large" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block size="large" loading={loading}>
              Login
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/pages/login/LoginPage.test.tsx
```

Expected: PASS — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/login/
git commit -m "feat(frontend): implement LoginPage with redirect and error handling"
```

---

### Task 8: ResumeListPage

**Files:**
- Modify: `frontend/src/pages/resumes/ResumeListPage.tsx`
- Test: `frontend/src/pages/resumes/ResumeListPage.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `frontend/src/pages/resumes/ResumeListPage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/resumes', () => ({
  listResumes: vi.fn(),
  deleteResume: vi.fn(),
  downloadResume: vi.fn(),
}));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ user: { username: 'alice' }, token: 'tok', isInitializing: false, login: vi.fn(), logout: vi.fn() })),
}));

import * as resumesApi from '../../api/resumes';
import ResumeListPage from './ResumeListPage';

const mockPage = {
  content: [
    { id: 'r1', fileName: 'alice_cv.pdf', fileType: 'PDF', source: 'MANUAL', status: 'PARSED', uploadedAt: '2026-03-01T10:00:00Z' },
  ],
  totalElements: 1, totalPages: 1, number: 0, size: 10,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(resumesApi.listResumes).mockResolvedValue(mockPage);
});

describe('ResumeListPage', () => {
  it('renders the resume table after loading', async () => {
    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('alice_cv.pdf')).toBeInTheDocument());
    expect(screen.getByText('PARSED')).toBeInTheDocument();
  });

  it('renders empty state when no resumes', async () => {
    vi.mocked(resumesApi.listResumes).mockResolvedValueOnce({ ...mockPage, content: [], totalElements: 0 });
    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/upload/i)).toBeInTheDocument());
  });

  it('calls deleteResume on confirm delete', async () => {
    vi.mocked(resumesApi.deleteResume).mockResolvedValueOnce(undefined);
    vi.mocked(resumesApi.listResumes).mockResolvedValue(mockPage);

    render(<MemoryRouter><ResumeListPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('alice_cv.pdf'));

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));
    // Antd Popconfirm renders an "OK" button in the popover
    await waitFor(() => screen.getByRole('button', { name: /ok/i }));
    await userEvent.click(screen.getByRole('button', { name: /ok/i }));

    await waitFor(() => expect(resumesApi.deleteResume).toHaveBeenCalledWith('r1'));
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/pages/resumes/ResumeListPage.test.tsx
```

Expected: FAIL — stub component.

- [ ] **Step 3: Implement `src/pages/resumes/ResumeListPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Table, Button, Tag, Popconfirm, Space, message, Empty } from 'antd';
import { useNavigate } from 'react-router-dom';
import { listResumes, deleteResume, downloadResume, type ResumeListItem, type Page } from '../../api/resumes';

const STATUS_COLORS: Record<string, string> = {
  UPLOADED: 'default', PARSING: 'processing', PARSED: 'success',
  PARSE_FAILED: 'error', VECTORIZING: 'processing', VECTORIZED: 'success',
};

export default function ResumeListPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState<Page<ResumeListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);

  const load = async (p: number) => {
    setLoading(true);
    try {
      const data = await listResumes(p - 1, 10);
      setPage(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(1); }, []);

  const handleDelete = async (id: string) => {
    try {
      await deleteResume(id);
      message.success('Resume deleted');
      load(current);
    } catch {
      message.error('Failed to delete resume');
    }
  };

  const handleDownload = async (record: ResumeListItem) => {
    try {
      const blob = await downloadResume(record.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = record.fileName;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      message.error('Download failed');
    }
  };

  const columns = [
    { title: 'Filename', dataIndex: 'fileName', key: 'fileName' },
    { title: 'Source', dataIndex: 'source', key: 'source' },
    {
      title: 'Status', dataIndex: 'status', key: 'status',
      render: (status: string) => <Tag color={STATUS_COLORS[status] ?? 'default'}>{status}</Tag>,
    },
    { title: 'Uploaded', dataIndex: 'uploadedAt', key: 'uploadedAt', render: (d: string) => new Date(d).toLocaleDateString() },
    {
      title: 'Actions', key: 'actions',
      render: (_: unknown, record: ResumeListItem) => (
        <Space>
          <Button size="small" onClick={() => handleDownload(record)}>Download</Button>
          <Popconfirm title="Delete this resume?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const isEmpty = page && page.totalElements === 0;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>Resumes</h2>
        <Button type="primary" onClick={() => navigate('/resumes/upload')}>Upload Resume</Button>
      </div>
      {isEmpty ? (
        <Empty description="No resumes yet. Upload your first resume to get started." />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={page?.content ?? []}
          loading={loading}
          pagination={{
            current, total: page?.totalElements ?? 0, pageSize: 10,
            onChange: (p) => { setCurrent(p); load(p); },
          }}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/pages/resumes/ResumeListPage.test.tsx
```

Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/resumes/ResumeListPage.tsx frontend/src/pages/resumes/ResumeListPage.test.tsx
git commit -m "feat(frontend): implement ResumeListPage with pagination and delete"
```

---

### Task 9: ResumeUploadPage

**Files:**
- Modify: `frontend/src/pages/resumes/ResumeUploadPage.tsx`
- Test: `frontend/src/pages/resumes/ResumeUploadPage.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `frontend/src/pages/resumes/ResumeUploadPage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/resumes', () => ({ uploadResume: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import * as resumesApi from '../../api/resumes';
import ResumeUploadPage from './ResumeUploadPage';

beforeEach(() => { vi.clearAllMocks(); });

describe('ResumeUploadPage', () => {
  it('renders upload dragger', () => {
    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);
    expect(screen.getByText(/drag/i)).toBeInTheDocument();
  });

  it('uploads file and redirects to /resumes on success', async () => {
    vi.mocked(resumesApi.uploadResume).mockResolvedValueOnce({
      id: 'r1', fileName: 'cv.pdf', fileType: 'PDF', source: 'MANUAL', status: 'UPLOADED', uploadedAt: '',
    });

    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);

    const file = new File(['pdf content'], 'cv.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(input, file);
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    await waitFor(() => expect(resumesApi.uploadResume).toHaveBeenCalledWith(file));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/resumes'));
  });

  it('shows error message on upload failure', async () => {
    const err = new Error('File too large');
    vi.mocked(resumesApi.uploadResume).mockRejectedValueOnce(err);

    render(<MemoryRouter><ResumeUploadPage /></MemoryRouter>);
    const file = new File(['pdf content'], 'cv.pdf', { type: 'application/pdf' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await userEvent.upload(input, file);
    await userEvent.click(screen.getByRole('button', { name: /upload/i }));

    await waitFor(() => expect(screen.getByText('File too large')).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/pages/resumes/ResumeUploadPage.test.tsx
```

Expected: FAIL.

- [ ] **Step 3: Implement `src/pages/resumes/ResumeUploadPage.tsx`**

```tsx
import { useState } from 'react';
import { Upload, Button, Alert, message } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { uploadResume } from '../../api/resumes';

const ACCEPTED = '.pdf,.doc,.docx';

export default function ResumeUploadPage() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleUpload = async () => {
    if (!file) return;
    setError(null);
    setLoading(true);
    try {
      await uploadResume(file);
      message.success('Resume uploaded successfully');
      navigate('/resumes');
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : 'Upload failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <h2>Upload Resume</h2>
      {error && <Alert message={error} type="error" style={{ marginBottom: 16 }} />}
      <Upload.Dragger
        accept={ACCEPTED}
        multiple={false}
        beforeUpload={(f) => { setFile(f); return false; }}
        onRemove={() => setFile(null)}
        maxCount={1}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">Click or drag a resume file to this area to upload</p>
        <p className="ant-upload-hint">Supports PDF, DOC, DOCX</p>
      </Upload.Dragger>
      <div style={{ marginTop: 16, display: 'flex', gap: 8 }}>
        <Button type="primary" disabled={!file} loading={loading} onClick={handleUpload}>
          Upload
        </Button>
        <Button onClick={() => navigate('/resumes')}>Cancel</Button>
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/pages/resumes/ResumeUploadPage.test.tsx
```

Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/resumes/ResumeUploadPage.tsx frontend/src/pages/resumes/ResumeUploadPage.test.tsx
git commit -m "feat(frontend): implement ResumeUploadPage"
```

---

## Chunk 4: Job Pages

### Task 10: JobListPage

**Files:**
- Modify: `frontend/src/pages/jobs/JobListPage.tsx`
- Test: `frontend/src/pages/jobs/JobListPage.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `frontend/src/pages/jobs/JobListPage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/jobs', () => ({
  listJobs: vi.fn(),
  deleteJob: vi.fn(),
}));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

import * as jobsApi from '../../api/jobs';
import JobListPage from './JobListPage';

const mockPage = {
  content: [
    { id: 'j1', title: 'Backend Engineer', departmentId: 'd1', departmentName: 'Engineering', status: 'DRAFT', createdAt: '2026-03-01T10:00:00Z' },
  ],
  totalElements: 1, totalPages: 1, number: 0, size: 10,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(jobsApi.listJobs).mockResolvedValue(mockPage);
});

describe('JobListPage', () => {
  it('renders job table after loading', async () => {
    render(<MemoryRouter><JobListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument());
    expect(screen.getByText('Engineering')).toBeInTheDocument();
  });

  it('renders empty state when no jobs', async () => {
    vi.mocked(jobsApi.listJobs).mockResolvedValueOnce({ ...mockPage, content: [], totalElements: 0 });
    render(<MemoryRouter><JobListPage /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/create/i)).toBeInTheDocument());
  });

  it('calls deleteJob on confirm delete', async () => {
    vi.mocked(jobsApi.deleteJob).mockResolvedValueOnce(undefined);
    render(<MemoryRouter><JobListPage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('button', { name: /delete/i }));
    await waitFor(() => screen.getByRole('button', { name: /ok/i }));
    await userEvent.click(screen.getByRole('button', { name: /ok/i }));

    await waitFor(() => expect(jobsApi.deleteJob).toHaveBeenCalledWith('j1'));
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/pages/jobs/JobListPage.test.tsx
```

Expected: FAIL.

- [ ] **Step 3: Implement `src/pages/jobs/JobListPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Table, Button, Tag, Popconfirm, Space, message, Empty } from 'antd';
import { useNavigate } from 'react-router-dom';
import { listJobs, deleteJob, type JobListItem, type Page } from '../../api/jobs';

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'default', PUBLISHED: 'success', PAUSED: 'warning', CLOSED: 'error',
};

export default function JobListPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState<Page<JobListItem> | null>(null);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);

  const load = async (p: number) => {
    setLoading(true);
    try {
      const data = await listJobs(p - 1, 10);
      setPage(data);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(1); }, []);

  const handleDelete = async (id: string) => {
    try {
      await deleteJob(id);
      message.success('Job deleted');
      load(current);
    } catch {
      message.error('Failed to delete job');
    }
  };

  const columns = [
    { title: 'Title', dataIndex: 'title', key: 'title' },
    { title: 'Department', dataIndex: 'departmentName', key: 'departmentName' },
    {
      title: 'Status', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={STATUS_COLORS[s] ?? 'default'}>{s}</Tag>,
    },
    { title: 'Created', dataIndex: 'createdAt', key: 'createdAt', render: (d: string) => new Date(d).toLocaleDateString() },
    {
      title: 'Actions', key: 'actions',
      render: (_: unknown, record: JobListItem) => (
        <Space>
          <Button size="small" onClick={() => navigate(`/jobs/${record.id}`)}>View</Button>
          <Popconfirm title="Delete this job?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const isEmpty = page && page.totalElements === 0;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>Jobs</h2>
        <Button type="primary" onClick={() => navigate('/jobs/new')}>Create JD</Button>
      </div>
      {isEmpty ? (
        <Empty description="No jobs yet. Create your first job description to get started." />
      ) : (
        <Table
          rowKey="id"
          columns={columns}
          dataSource={page?.content ?? []}
          loading={loading}
          pagination={{
            current, total: page?.totalElements ?? 0, pageSize: 10,
            onChange: (p) => { setCurrent(p); load(p); },
          }}
        />
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/pages/jobs/JobListPage.test.tsx
```

Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/jobs/JobListPage.tsx frontend/src/pages/jobs/JobListPage.test.tsx
git commit -m "feat(frontend): implement JobListPage with pagination and delete"
```

---

### Task 11: JobCreatePage

**Files:**
- Modify: `frontend/src/pages/jobs/JobCreatePage.tsx`
- Test: `frontend/src/pages/jobs/JobCreatePage.test.tsx`

- [ ] **Step 1: Write failing tests**

Create `frontend/src/pages/jobs/JobCreatePage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../../api/jobs', () => ({ createJob: vi.fn() }));
vi.mock('../../api/departments', () => ({ listDepartments: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return { ...actual, useNavigate: () => mockNavigate };
});

import * as jobsApi from '../../api/jobs';
import * as deptApi from '../../api/departments';
import JobCreatePage from './JobCreatePage';

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(deptApi.listDepartments).mockResolvedValue([{ id: 'd1', name: 'Engineering' }]);
});

describe('JobCreatePage', () => {
  it('renders all required form fields', async () => {
    render(<MemoryRouter><JobCreatePage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Engineering'));
    expect(screen.getByPlaceholderText(/title/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/description/i)).toBeInTheDocument();
  });

  it('submits form and redirects to new job detail page', async () => {
    vi.mocked(jobsApi.createJob).mockResolvedValueOnce({
      id: 'j1', title: 'SWE', departmentId: 'd1', departmentName: 'Engineering',
      status: 'DRAFT', createdAt: '', description: 'desc',
    });

    render(<MemoryRouter><JobCreatePage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Engineering'));

    await userEvent.type(screen.getByPlaceholderText(/title/i), 'SWE');

    // Select department
    await userEvent.click(screen.getByRole('combobox'));
    await waitFor(() => screen.getByText('Engineering'));
    await userEvent.click(screen.getByText('Engineering'));

    await userEvent.type(screen.getByPlaceholderText(/description/i), 'desc');
    await userEvent.click(screen.getByRole('button', { name: /submit/i }));

    await waitFor(() => expect(jobsApi.createJob).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'SWE', departmentId: 'd1', description: 'desc' })
    ));
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/jobs/j1'));
  });

  it('shows per-field validation errors on 400 response', async () => {
    const err: Error & { data?: unknown } = new Error('Validation failed');
    err.data = { title: 'Title is required' };
    vi.mocked(jobsApi.createJob).mockRejectedValueOnce(err);
    vi.mocked(deptApi.listDepartments).mockResolvedValue([{ id: 'd1', name: 'Engineering' }]);

    render(<MemoryRouter><JobCreatePage /></MemoryRouter>);
    await waitFor(() => screen.getByText('Engineering'));

    await userEvent.type(screen.getByPlaceholderText(/title/i), 'x');
    await userEvent.click(screen.getByRole('combobox'));
    await userEvent.click(screen.getByText('Engineering'));
    await userEvent.type(screen.getByPlaceholderText(/description/i), 'x');
    await userEvent.click(screen.getByRole('button', { name: /submit/i }));

    await waitFor(() => expect(screen.getByText('Title is required')).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/pages/jobs/JobCreatePage.test.tsx
```

Expected: FAIL.

- [ ] **Step 3: Implement `src/pages/jobs/JobCreatePage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Form, Input, Select, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { createJob, type CreateJobRequest } from '../../api/jobs';
import { listDepartments, type Department } from '../../api/departments';

export default function JobCreatePage() {
  const navigate = useNavigate();
  const [form] = Form.useForm();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    listDepartments().then(setDepartments).catch(() => {});
  }, []);

  const onFinish = async (values: CreateJobRequest) => {
    setLoading(true);
    try {
      const job = await createJob(values);
      navigate(`/jobs/${job.id}`);
    } catch (e: unknown) {
      if (e instanceof Error && (e as Error & { data?: Record<string, string> }).data) {
        const fieldErrors = (e as Error & { data: Record<string, string> }).data;
        form.setFields(
          Object.entries(fieldErrors).map(([name, errors]) => ({ name, errors: [errors] }))
        );
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: 720 }}>
      <h2>Create Job Description</h2>
      <Form form={form} layout="vertical" onFinish={onFinish}>
        <Form.Item name="title" label="Title" rules={[{ required: true }]}>
          <Input placeholder="Title" />
        </Form.Item>
        <Form.Item name="departmentId" label="Department" rules={[{ required: true }]}>
          <Select placeholder="Select department">
            {departments.map((d) => (
              <Select.Option key={d.id} value={d.id}>{d.name}</Select.Option>
            ))}
          </Select>
        </Form.Item>
        <Form.Item name="description" label="Description" rules={[{ required: true }]}>
          <Input.TextArea rows={4} placeholder="Description" />
        </Form.Item>
        <Form.Item name="requirements" label="Requirements">
          <Input.TextArea rows={3} placeholder="Requirements" />
        </Form.Item>
        <Form.Item name="skills" label="Skills">
          <Input placeholder="Skills (e.g. Java, Python, SQL)" />
        </Form.Item>
        <Form.Item name="education" label="Education">
          <Input placeholder="Education" />
        </Form.Item>
        <Form.Item name="experience" label="Experience">
          <Input placeholder="Experience" />
        </Form.Item>
        <Form.Item name="salaryRange" label="Salary Range">
          <Input placeholder="Salary Range" />
        </Form.Item>
        <Form.Item name="location" label="Location">
          <Input placeholder="Location" />
        </Form.Item>
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={loading}>Submit</Button>
          <Button style={{ marginLeft: 8 }} onClick={() => navigate('/jobs')}>Cancel</Button>
        </Form.Item>
      </Form>
    </div>
  );
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd frontend && npm run test -- --run src/pages/jobs/JobCreatePage.test.tsx
```

Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/jobs/JobCreatePage.tsx frontend/src/pages/jobs/JobCreatePage.test.tsx
git commit -m "feat(frontend): implement JobCreatePage with department select and validation"
```

---

## Chunk 5: JobDetailPage

### Task 12: JobDetailPage — JD Info Section

**Files:**
- Modify: `frontend/src/pages/jobs/JobDetailPage.tsx`
- Test: `frontend/src/pages/jobs/JobDetailPage.test.tsx`

- [ ] **Step 1: Write failing tests for the JD info section**

Create `frontend/src/pages/jobs/JobDetailPage.test.tsx`:

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';

vi.mock('../../api/jobs', () => ({
  getJob: vi.fn(),
  updateJob: vi.fn(),
  changeJobStatus: vi.fn(),
  deleteJob: vi.fn(),
}));
vi.mock('../../api/departments', () => ({ listDepartments: vi.fn() }));
vi.mock('../../api/match', () => ({ match: vi.fn() }));
vi.mock('../../context/AuthContext', () => ({
  useAuth: vi.fn(() => ({ token: 'tok', isInitializing: false, user: null, login: vi.fn(), logout: vi.fn() })),
}));

import * as jobsApi from '../../api/jobs';
import * as deptApi from '../../api/departments';
import * as matchApi from '../../api/match';
import JobDetailPage from './JobDetailPage';

const mockJob = {
  id: 'j1', title: 'Backend Engineer', departmentId: 'd1', departmentName: 'Engineering',
  status: 'DRAFT', createdAt: '2026-03-01T00:00:00Z',
  description: 'Build APIs', requirements: 'Java 3yrs', skills: 'Java, Spring',
  education: "Bachelor's", experience: '3 years', salaryRange: '20-30k', location: 'Beijing',
};

function renderDetailPage() {
  return render(
    <MemoryRouter initialEntries={['/jobs/j1']}>
      <Routes>
        <Route path="/jobs/:id" element={<JobDetailPage />} />
      </Routes>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(jobsApi.getJob).mockResolvedValue(mockJob);
  vi.mocked(deptApi.listDepartments).mockResolvedValue([{ id: 'd1', name: 'Engineering' }]);
  vi.mocked(matchApi.match).mockResolvedValue({ jobId: 'j1', results: [] });
});

describe('JobDetailPage — JD Info', () => {
  it('displays job details after load', async () => {
    renderDetailPage();
    await waitFor(() => expect(screen.getByText('Backend Engineer')).toBeInTheDocument());
    expect(screen.getByText('Build APIs')).toBeInTheDocument();
    expect(screen.getByText('Java 3yrs')).toBeInTheDocument();
  });

  it('calls changeJobStatus when status is changed', async () => {
    vi.mocked(jobsApi.changeJobStatus).mockResolvedValueOnce({ ...mockJob, status: 'PUBLISHED' });
    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    // Open status select and pick PUBLISHED
    await userEvent.click(screen.getByRole('combobox'));
    await waitFor(() => screen.getByText('PUBLISHED'));
    await userEvent.click(screen.getByText('PUBLISHED'));

    await waitFor(() => expect(jobsApi.changeJobStatus).toHaveBeenCalledWith('j1', 'PUBLISHED'));
  });

  it('shows inline status error on invalid transition', async () => {
    const err = new Error('Invalid status transition');
    vi.mocked(jobsApi.changeJobStatus).mockRejectedValueOnce(err);
    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('combobox'));
    await waitFor(() => screen.getByText('PUBLISHED'));
    await userEvent.click(screen.getByText('PUBLISHED'));

    await waitFor(() => expect(screen.getByText('Invalid status transition')).toBeInTheDocument());
  });

  it('enters edit mode and saves via PUT', async () => {
    vi.mocked(jobsApi.updateJob).mockResolvedValueOnce({ ...mockJob, title: 'Updated Title' });
    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('button', { name: /edit/i }));
    const titleInput = screen.getByDisplayValue('Backend Engineer');
    await userEvent.clear(titleInput);
    await userEvent.type(titleInput, 'Updated Title');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));

    await waitFor(() => expect(jobsApi.updateJob).toHaveBeenCalledWith('j1', expect.objectContaining({ title: 'Updated Title' })));
  });
});

describe('JobDetailPage — AI Matching', () => {
  it('runs match and shows results table', async () => {
    vi.mocked(matchApi.match).mockResolvedValueOnce({
      jobId: 'j1',
      results: [
        { resumeId: 'r1', vectorScore: 0.95, llmScore: 87, reasoning: 'Strong match', highlights: ['Java'] },
      ],
    });

    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));

    await userEvent.click(screen.getByRole('button', { name: /find matching/i }));

    await waitFor(() => expect(screen.getByText('Strong match')).toBeInTheDocument());
    expect(screen.getByText('Java')).toBeInTheDocument();
  });

  it('shows 422 warning when JD not indexed', async () => {
    const err = new Error('JD not vectorized') as Error & { status?: number };
    err.status = 422;
    // Simulate 422 by having match throw with a specific message
    vi.mocked(matchApi.match).mockRejectedValueOnce(Object.assign(new Error('JD not vectorized yet'), { status: 422 }));

    // We need the request helper to set status on error — for the test, match() throws an error
    // whose message contains 422-related text. JobDetailPage checks error message for routing.
    // For the test, we check the warning text appears.

    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));
    await userEvent.click(screen.getByRole('button', { name: /find matching/i }));

    await waitFor(() => expect(screen.getByText(/not been indexed/i)).toBeInTheDocument());
  });

  it('shows 503 error when AI service is down', async () => {
    vi.mocked(matchApi.match).mockRejectedValueOnce(Object.assign(new Error('AI service unavailable'), { status: 503 }));

    renderDetailPage();
    await waitFor(() => screen.getByText('Backend Engineer'));
    await userEvent.click(screen.getByRole('button', { name: /find matching/i }));

    await waitFor(() => expect(screen.getByText(/unavailable/i)).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd frontend && npm run test -- --run src/pages/jobs/JobDetailPage.test.tsx
```

Expected: FAIL — stub component.

- [ ] **Step 3: Update `src/api/request.ts` to attach HTTP status on errors**

Errors thrown by `request()` need a `status` property so pages can distinguish 422 vs 503. Edit `request.ts`:

```ts
// In the non-2xx branch, before throwing, attach the status code:
const err: Error & { status?: number; data?: unknown } = new Error(body.message ?? `HTTP ${response.status}`);
err.status = response.status;
if (response.status === 400) err.data = body.data;
throw err;
```

Update the 401 branch similarly:
```ts
const err: Error & { status?: number } = new Error(body.message ?? 'Unauthorized');
err.status = 401;
throw err;
```

- [ ] **Step 4: Run request.test.ts to confirm it still passes**

```bash
cd frontend && npm run test -- --run src/api/request.test.ts
```

Expected: PASS — all 7 tests.

- [ ] **Step 5: Implement `src/pages/jobs/JobDetailPage.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Descriptions, Tag, Select, Button, Form, Input, Divider, Alert,
  InputNumber, Spin, Table, Tooltip, Space, message,
} from 'antd';
import { getJob, updateJob, changeJobStatus, type JobDetail, type UpdateJobRequest } from '../../api/jobs';
import { listDepartments, type Department } from '../../api/departments';
import { match, type MatchResultItem } from '../../api/match';

const STATUS_TRANSITIONS: Record<string, string[]> = {
  DRAFT: ['PUBLISHED'],
  PUBLISHED: ['PAUSED', 'CLOSED'],
  PAUSED: ['PUBLISHED', 'CLOSED'],
  CLOSED: [],
};

const STATUS_COLORS: Record<string, string> = {
  DRAFT: 'default', PUBLISHED: 'success', PAUSED: 'warning', CLOSED: 'error',
};

const LLM_COLOR = (score: number) => score >= 80 ? 'green' : score >= 60 ? 'orange' : 'red';

export default function JobDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [job, setJob] = useState<JobDetail | null>(null);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [editing, setEditing] = useState(false);
  const [editForm] = Form.useForm();
  const [statusError, setStatusError] = useState<string | null>(null);

  const [matchLoading, setMatchLoading] = useState(false);
  const [topK, setTopK] = useState(10);
  const [matchResults, setMatchResults] = useState<MatchResultItem[] | null>(null);
  const [matchError, setMatchError] = useState<{ type: '422' | '503' | 'generic'; message: string } | null>(null);

  useEffect(() => {
    if (!id) return;
    getJob(id).then(setJob).catch(() => message.error('Failed to load job'));
    listDepartments().then(setDepartments).catch(() => {});
  }, [id]);

  const handleStatusChange = async (status: string) => {
    if (!id) return;
    setStatusError(null);
    try {
      const updated = await changeJobStatus(id, status);
      setJob(updated);
    } catch (e: unknown) {
      setStatusError(e instanceof Error ? e.message : 'Status change failed');
    }
  };

  const handleEdit = () => {
    if (!job) return;
    editForm.setFieldsValue(job);
    setEditing(true);
  };

  const handleSave = async () => {
    if (!id) return;
    try {
      const values: UpdateJobRequest = await editForm.validateFields();
      const updated = await updateJob(id, values);
      setJob(updated);
      setEditing(false);
    } catch (e: unknown) {
      if (e instanceof Error && (e as Error & { data?: Record<string, string> }).data) {
        const fieldErrors = (e as Error & { data: Record<string, string> }).data;
        editForm.setFields(
          Object.entries(fieldErrors).map(([name, errors]) => ({ name, errors: [errors] }))
        );
      }
    }
  };

  const handleMatch = async () => {
    if (!id) return;
    setMatchError(null);
    setMatchResults(null);
    setMatchLoading(true);
    try {
      const result = await match(id, topK);
      setMatchResults(result.results);
    } catch (e: unknown) {
      const status = (e as Error & { status?: number }).status;
      const msg = e instanceof Error ? e.message : 'Match failed';
      if (status === 422) {
        setMatchError({ type: '422', message: msg });
      } else if (status === 503) {
        setMatchError({ type: '503', message: msg });
      } else {
        setMatchError({ type: 'generic', message: msg });
      }
    } finally {
      setMatchLoading(false);
    }
  };

  if (!job) return <Spin />;

  const nextStatuses = STATUS_TRANSITIONS[job.status] ?? [];

  const matchColumns = [
    { title: '#', key: 'rank', render: (_: unknown, __: unknown, i: number) => i + 1, width: 50 },
    {
      title: 'Resume ID', dataIndex: 'resumeId', key: 'resumeId',
      render: (id: string) => <Tooltip title={id}>{id.slice(0, 8)}…</Tooltip>,
    },
    { title: 'Vector Score', dataIndex: 'vectorScore', key: 'vectorScore', render: (v: number) => v.toFixed(2) },
    {
      title: 'LLM Score', dataIndex: 'llmScore', key: 'llmScore',
      render: (v: number) => <span style={{ color: LLM_COLOR(v), fontWeight: 600 }}>{v}</span>,
    },
    {
      title: 'Reasoning', dataIndex: 'reasoning', key: 'reasoning',
      render: (text: string) => (
        <Tooltip title={text}>{text.length > 80 ? `${text.slice(0, 80)}…` : text}</Tooltip>
      ),
    },
    {
      title: 'Highlights', dataIndex: 'highlights', key: 'highlights',
      render: (tags: string[]) => <Space wrap>{tags.map((t) => <Tag key={t}>{t}</Tag>)}</Space>,
    },
  ];

  return (
    <div>
      {/* Section 1: JD Info */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>{job.title}</h2>
        {!editing && <Button onClick={handleEdit}>Edit</Button>}
      </div>

      {!editing ? (
        <>
          <div style={{ marginBottom: 12 }}>
            <Tag color={STATUS_COLORS[job.status] ?? 'default'}>{job.status}</Tag>
            {nextStatuses.length > 0 && (
              <Select
                style={{ width: 160, marginLeft: 8 }}
                placeholder="Change status"
                onChange={handleStatusChange}
                value={undefined}
              >
                {nextStatuses.map((s) => (
                  <Select.Option key={s} value={s}>{s}</Select.Option>
                ))}
              </Select>
            )}
            {statusError && <span style={{ color: 'red', marginLeft: 8 }}>{statusError}</span>}
          </div>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label="Department">{job.departmentName}</Descriptions.Item>
            <Descriptions.Item label="Description">{job.description}</Descriptions.Item>
            {job.requirements && <Descriptions.Item label="Requirements">{job.requirements}</Descriptions.Item>}
            {job.skills && <Descriptions.Item label="Skills">{job.skills}</Descriptions.Item>}
            {job.education && <Descriptions.Item label="Education">{job.education}</Descriptions.Item>}
            {job.experience && <Descriptions.Item label="Experience">{job.experience}</Descriptions.Item>}
            {job.salaryRange && <Descriptions.Item label="Salary Range">{job.salaryRange}</Descriptions.Item>}
            {job.location && <Descriptions.Item label="Location">{job.location}</Descriptions.Item>}
          </Descriptions>
        </>
      ) : (
        <Form form={editForm} layout="vertical">
          <Form.Item name="title" label="Title" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="departmentId" label="Department" rules={[{ required: true }]}>
            <Select>
              {departments.map((d) => (
                <Select.Option key={d.id} value={d.id}>{d.name}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="description" label="Description" rules={[{ required: true }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
          <Form.Item name="requirements" label="Requirements"><Input.TextArea rows={3} /></Form.Item>
          <Form.Item name="skills" label="Skills"><Input /></Form.Item>
          <Form.Item name="education" label="Education"><Input /></Form.Item>
          <Form.Item name="experience" label="Experience"><Input /></Form.Item>
          <Form.Item name="salaryRange" label="Salary Range"><Input /></Form.Item>
          <Form.Item name="location" label="Location"><Input /></Form.Item>
          <Form.Item>
            <Button type="primary" onClick={handleSave}>Save</Button>
            <Button style={{ marginLeft: 8 }} onClick={() => setEditing(false)}>Cancel</Button>
          </Form.Item>
        </Form>
      )}

      <Divider />

      {/* Section 2: AI Matching */}
      <h3>AI Matching</h3>
      <Space style={{ marginBottom: 16 }}>
        <InputNumber min={1} max={50} value={topK} onChange={(v) => setTopK(v ?? 10)} addonBefore="Top K" />
        <Button type="primary" onClick={handleMatch} loading={matchLoading}>
          Find Matching Resumes
        </Button>
      </Space>

      {matchError?.type === '422' && (
        <Alert
          type="warning"
          message="This JD has not been indexed yet. It will be ready shortly after creation."
          style={{ marginBottom: 16 }}
        />
      )}
      {matchError?.type === '503' && (
        <Alert
          type="error"
          message="AI matching service is currently unavailable."
          style={{ marginBottom: 16 }}
        />
      )}
      {matchError?.type === 'generic' && (
        <Alert type="error" message={matchError.message} style={{ marginBottom: 16 }} />
      )}

      {matchResults !== null && (
        matchResults.length === 0 ? (
          <Alert type="info" message="No matching resumes found." />
        ) : (
          <Table
            rowKey="resumeId"
            columns={matchColumns}
            dataSource={matchResults}
            pagination={false}
            size="small"
          />
        )
      )}
    </div>
  );
}
```

- [ ] **Step 6: Run all JobDetailPage tests**

```bash
cd frontend && npm run test -- --run src/pages/jobs/JobDetailPage.test.tsx
```

Expected: PASS — 7 tests.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/api/request.ts frontend/src/pages/jobs/JobDetailPage.tsx frontend/src/pages/jobs/JobDetailPage.test.tsx
git commit -m "feat(frontend): implement JobDetailPage with edit, status change, and AI matching"
```

---

## Chunk 6: Final Verification

### Task 13: Full Test Suite + Build Verification

**Files:** No new files — verification only.

- [ ] **Step 1: Run the full test suite**

```bash
cd frontend && npm run test -- --run
```

Expected: All tests pass. Total: request (7) + auth (3) + resumes (4) + jobs (6) + departments (1) + match (1) + AuthContext (5) + ProtectedRoute (3) + LoginPage (4) + ResumeListPage (3) + ResumeUploadPage (3) + JobListPage (3) + JobCreatePage (3) + JobDetailPage (7) = **53 tests**.

- [ ] **Step 2: Run TypeScript type check**

```bash
cd frontend && npx tsc --noEmit
```

Expected: No type errors.

- [ ] **Step 3: Run production build**

```bash
cd frontend && npm run build
```

Expected: Build completes successfully, no warnings about missing imports.

- [ ] **Step 4: Final commit and push**

```bash
git add frontend/
git commit -m "feat(frontend): complete frontend MVP — auth, resumes, jobs, AI matching"
```

Then invoke `superpowers:finishing-a-development-branch` to decide how to integrate.
