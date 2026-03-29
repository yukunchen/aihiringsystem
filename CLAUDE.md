# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI-powered recruitment platform (SaaS) for unified resume management across job platforms (Boss Zhipin integration + manual upload) with intelligent JD-to-resume matching. Primary language context is Chinese.

## Architecture

Two-service architecture:

1. **Core Business Service** (Spring Boot / Java) — user/auth, departments, resumes, JD management, permissions, Boss Zhipin integration
2. **AI Matching Service** (Python / FastAPI) — resume/JD vectorization, vector DB search (Milvus/Qdrant), LLM-based match scoring and reasoning

**Data stores**: PostgreSQL (structured data), Milvus/Qdrant (vectors), MinIO/OSS (file storage)
**Frontend**: React + Ant Design
**Infra**: Docker, GitHub Actions CI/CD, Kong/Nginx API gateway

## Development Methodology

- **TDD**: Red-green-refactor. Write tests first.
- **Git Worktree**: Parallel feature development in isolated worktrees under `worktrees/` directory. Each module gets its own worktree (e.g., `worktrees/feature-user/`, `worktrees/feature-resume/`).
- Modules are designed for high independence — user, resume, JD modules can be developed independently; AI matching depends on resume + JD; Boss integration depends on resume.

## Key Design Spec

Full system design document: `docs/superpowers/specs/2026-03-14-recruitment-platform-design.md`

Covers: role-based permissions (super admin / HR admin / dept admin / user), resume parsing pipeline (upload → file storage → text extraction → AI structuring → vectorization → DB), AI matching flow (JD → vectorize → top-K retrieval → LLM reasoning), data models, and API contracts.

## API Conventions

REST APIs under `/api/` prefix:
- Auth: `/api/auth/login`, `/api/auth/refresh`
- Users: `/api/users`
- Resumes: `/api/resumes`, `/api/resumes/upload`, `/api/resumes/{id}`
- Jobs: `/api/jobs`, `/api/jobs/{id}`
- Match: `/api/match`
- Boss integration: `/api/boss/auth-url`, `/api/boss/callback`, `/api/boss/sync`

## Auth

JWT-based: access token (2h TTL) + refresh token (7d TTL).

## Execution Principles

- Prefer minimal, reversible changes.
- Respect existing architecture boundaries between:
  - Core Business Service (Spring Boot / Java)
  - AI Matching Service (Python / FastAPI)
  - Frontend (React + Ant Design)
- Do not silently change API contracts, DB schema, auth behavior, or cross-service interfaces unless the task explicitly requires it.
- For issue-driven work, do not treat the reporter's suspected cause as the confirmed root cause. Always verify.

## Definition of Done

A task is not complete unless the final expected behavior is verified, not just the suspected root cause.

Claude must not claim completion unless all applicable items below are satisfied:
- relevant functionality is verified end-to-end
- relevant database state is verified when initialization, migration, or seed data is involved
- relevant API behavior is verified when frontend data depends on it
- relevant UI behavior is verified when the issue is user-visible
- verification is performed in the target environment when the issue is environment-specific
- verification evidence is included in the response

Do not use vague statements such as:
- fixed
- resolved
- should work now
- completed

without verification evidence.

## UI / Data Integration Bug Rules

For bugs involving any of the following:
- empty dropdowns
- missing UI data
- broken forms
- missing seeded data
- page loads but visible content is wrong
- user-visible regressions
- environment-specific data inconsistencies

Claude must verify all applicable layers before claiming the issue is fixed:

1. Database layer
   - confirm relevant table exists
   - confirm row count is non-zero when non-empty data is expected
   - provide sample rows when relevant

2. API layer
   - verify the exact endpoint used by the frontend
   - confirm response status
   - confirm response count / non-empty result when expected
   - provide sample payload when relevant

3. UI layer
   - verify the final visible result on the target page
   - include screenshot or browser automation evidence when the issue is user-visible

4. Reproduction layer
   - repeat the original reproduction steps
   - confirm the original issue no longer reproduces

## Do Not Claim Fix By Assumption

Claude must not claim a bug is fixed only because:
- a migration was added
- a seed script was added
- code compiles
- tests unrelated to the issue pass
- an API returns HTTP 200
- local behavior appears correct in a different environment
- a likely root cause was patched without verifying the visible result

For user-visible bugs, the visible result must be verified.
If verification is incomplete, Claude must respond with one of:
- BLOCKED
- NOT VERIFIED
- READY FOR REVIEW

instead of FIXED.

## Issue Workflow Constraints

For GitHub issue-driven fixes:
- Claude may analyze issues and open PRs
- Claude must not directly close UI/data integration issues
- Claude must not directly close issues that were not verified in the target environment
- such issues should move to `waiting-review`, `qa-needed`, or `blocked` after evidence is submitted
- a human must confirm the visible fix in the target environment before the issue is closed

## Required Response Structure For Bug Fixes

For bug fixes, Claude responses in issue comments or PR descriptions must include all applicable sections:

- Status
- Root cause
- Changes made
- DB verification
- API verification
- UI verification
- Commands/tests run
- Residual risks
- Recommended next issue state

Do not omit verification sections when they are relevant.

## Environment-Specific Verification

When a bug is reported against a specific environment, Claude must explicitly state:
- exact environment verified
- exact URL verified
- exact account / role used for verification
- whether verification was local only, staging only, or target environment verified

If Claude cannot verify the target environment, it must say so explicitly.

## Database Initialization Rules

For any task involving lookup data, reference data, forms, dropdowns, tenant defaults, departments, roles, or other required application data:

Claude must verify all applicable items:
- migration behavior
- seed behavior
- row count after seed
- whether the application depends on non-empty reference data
- whether local/staging/target environment initialization paths are aligned

Do not assume DB initialization is already complete.

## Frontend Verification Expectations

For frontend work, do not stop at build success.

When relevant, verify:
- loading state
- empty state
- error state
- field mapping correctness
- null / missing field tolerance
- form submission path
- persisted result after refresh
- visible correctness of the final user-facing result

## Testing Expectations

TDD still applies, but tests alone do not replace issue-specific verification.

For bug fixes:
- add or update tests when appropriate
- prefer regression tests for recurring bugs
- for user-visible bugs, add or update browser/E2E checks when feasible
- if a bug cannot be adequately covered by existing tests, say so explicitly

## Preferred Status Vocabulary

Use these status values in issue/PR communication:
- ANALYZING
- NEEDS CLARIFICATION
- READY FOR REVIEW
- BLOCKED
- NOT VERIFIED

Avoid using FIXED unless the required verification evidence is already included.

## Human Review Gate

For user-visible bugs, environment-specific bugs, and data initialization bugs:
- Claude may prepare a fix
- Claude may prepare evidence
- Claude may recommend `waiting-review`
- only a human should make the final closure decision
