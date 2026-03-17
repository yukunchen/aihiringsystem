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
