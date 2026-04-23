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

## Debugging Rules

- **同方向连续失败 2 次，必须停下来换方向。** 不要在同一个假设上尝试第 3 次。
- **HTTP 403/500 先查日志，不要猜。** 运行 `docker logs <container>` 看实际异常，再决定修什么。
- **修 bug 前先做对比测试。** 同类端点是否也失败？如果只有一个端点失败，问题在该端点的代码，不在通用层（权限、认证、数据库）。
- **永远通过前端代理验证，不要只测直连端口。** 用户访问的是 nginx 代理路径，直连 backend 端口能通不代表用户能用。

## Deployment Rules

- 使用 `docker compose down && docker compose up -d` 而非 `docker compose restart`，确保环境变量和新镜像生效。
- dev/staging/prod 使用独立的 Docker 内部网络，禁止共享网络导致 DNS 冲突。
- 部署后必须通过前端端口验证，不能只验证 backend 直连端口。

## Autofix Workflow (issue-driven autonomous fixes)

**Session roles.** Two kinds of Claude sessions share this system:
- **Fixer** — long-running background session on the VPS. Consumes `new_issue` notifications, claims, writes fixes, opens PRs. Should run with `CLAUDE_ROLE=fixer`.
- **Orchestrator** — interactive session talking to the human. Consumes `pr_ready` pings (so the human is told a PR is waiting) and deploy notifications. Should run with `CLAUDE_ROLE=orchestrator`.

The notification hook (`scripts/check-notifications.sh`, canonical source in this repo; deployed to `/opt/ai-hiring/scripts/`) filters queue entries by role so the fixer does not swallow pings meant for the human, and vice versa. Notifications flow:
- `/opt/ai-hiring/notifications/queue.jsonl` — `new_issue`, deploy events (fixer + fallback).
- `/opt/ai-hiring/notifications/orchestrator-queue.jsonl` — `pr_ready` and future orchestrator-only events.

**Claim before work (fixer).** Before starting on an `autofix`-labeled issue:

```bash
scripts/autofix-claim.sh <issue-number>
```

The script checks that the issue is still OPEN, has no `autofix-in-progress` label, and has no open PR that would close it; then it adds the `autofix-in-progress` label as an advisory lock. If it exits non-zero, skip the issue — another session is already on it or it has been resolved.

The label is removed automatically when the fixing PR is merged (because the issue closes). If a session abandons the work without a PR, run `scripts/autofix-release.sh <issue-number>` to free the lock.

**PR-ready pings (orchestrator).** `.github/workflows/notify-pr-ready.yml` writes a `pr_ready` entry to the orchestrator queue whenever a non-draft PR is opened, so the orchestrator surfaces pending reviews to the human on the next prompt. Do not merge on the human's behalf — per the Git Workflow rule below, every merge is a human decision.

**Out-of-band push to Discord.** `scripts/discord-notify-watcher.sh` is a systemd-managed daemon (`ai-hiring-discord-notify.service`) that polls the orchestrator queue every 5s, POSTs unpushed `orchestrator`-targeted entries to a Discord webhook, and sets `pushed_discord: true` on each delivered entry. It uses a separate flag from `read` so the orchestrator hook can still pick the same entry up for in-session context. Webhook URL lives in `/etc/ai-hiring/discord.env` (chmod 600, never committed).

## Git Workflow

- **所有进入 master 的改动都必须经过用户 review。** 这是不可违反的基本规则。无论改动多小、多紧急，一律创建 PR 等用户 merge，没有例外。
- 永远不要直接 push 到 master。必须创建 feature branch 并开 PR。
- 永远不要自行 merge PR。等用户 review 后由用户 merge。
- **永远不要用 `git add -A` 或 `git add .`。** 必须逐个指定要提交的文件（`git add file1 file2`），避免把 uploads、__pycache__、临时文件等无关内容提交进去。
- commit 只包含当前任务相关的文件，不要捆绑无关改动。提交前用 `git diff --cached --stat` 确认文件列表。
- 已 merge 的 PR 分支不能再 push 新 commit（不会进入 master）。如果有后续修改，必须开新分支、新 PR。

## Evidence & Screenshot Rules

- 在 GitHub issue/PR 中附截图时，**必须使用 GitHub 能渲染的 URL**。不能用本地路径（如 `test-results/xxx.png`），GitHub 上看不到。
- 正确做法：将截图 commit 到仓库（如 `docs/evidence/`）并用 `https://raw.githubusercontent.com/...` 链接，或通过 GitHub issue 上传获取 URL。
- 关闭 issue 前必须确认截图在 GitHub 页面上能正确显示。

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
