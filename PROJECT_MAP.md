# Project Map — AI Hiring System

> 最后更新：2026-03-28
>
> AI 驱动的 SaaS 招聘平台，支持简历统一管理（Boss 直聘集成 + 手动上传）和智能 JD-简历匹配。

---

## 目录

1. [架构总览](#架构总览)
2. [仓库目录结构](#仓库目录结构)
3. [服务详情](#服务详情)
   - [后端 — ai-hiring-backend](#后端--ai-hiring-backend)
   - [AI 匹配服务 — ai-matching-service](#ai-匹配服务--ai-matching-service)
   - [前端 — frontend](#前端--frontend)
4. [数据库迁移](#数据库迁移)
5. [Docker & 多环境配置](#docker--多环境配置)
6. [GitHub Actions 工作流](#github-actions-工作流)
7. [VPS 部署脚本](#vps-部署脚本)
8. [自动化 Issue → PR → Staging 工作流](#自动化-issue--pr--staging-工作流)
9. [端口规划](#端口规划)
10. [文档 & 规范](#文档--规范)

---

## 架构总览

```
                    ┌─────────────────────────────────────┐
                    │            GitHub                   │
                    │  Issues (autofix) → PRs → Actions   │
                    └──────────────┬──────────────────────┘
                                   │ SSH deploy
                    ┌──────────────▼──────────────────────┐
                    │          VPS (Ubuntu)               │
                    │                                     │
                    │  Nginx :80 ──► Frontend :3010 (prod)│
                    │               Frontend :3002 (stag) │
                    │                                     │
                    │  Backend     :8080 (prod)           │
                    │              :8082 (staging)        │
                    │                                     │
                    │  AI Service  :8000 (prod)           │
                    │              :8001 (staging)        │
                    │                                     │
                    │  PostgreSQL  :5432                  │
                    │  MinIO       :9000                  │
                    └─────────────────────────────────────┘
```

**两服务架构：**

| 服务 | 技术栈 | 职责 |
|------|--------|------|
| Core Business Service | Spring Boot 3 / Java 17 | 认证、用户、部门、简历、JD、权限 |
| AI Matching Service | Python 3.11 / FastAPI | 向量化、向量检索、LLM 匹配评分 |
| Frontend | React 18 / TypeScript / Ant Design | 用户界面 |
| PostgreSQL | 16 | 结构化数据 |
| MinIO | latest | 文件存储（简历原文件） |
| Milvus/Qdrant | - | 向量数据库（计划中） |

---

## 仓库目录结构

```
aihiringsystem-master/
├── CLAUDE.md                    # Claude Code 项目指导（架构、开发方法论）
├── PROJECT_MAP.md               # 本文件
├── README.md                    # 项目说明
├── docker-compose.yml           # 本地开发快速启动（所有服务）
├── .gitignore
│
├── .github/
│   └── workflows/
│       ├── ci.yml               # 持续集成：Java 测试 + Python 测试 + 前端 lint/build
│       ├── deploy-dev.yml       # 推送到 dev 分支自动部署开发环境
│       ├── deploy-staging.yml   # 推送到 master 自动部署 staging
│       └── deploy-prod.yml      # 手动触发部署生产环境
│
├── ai-hiring-backend/           # Spring Boot 后端
├── ai-matching-service/         # Python FastAPI AI 服务
├── frontend/                    # React TypeScript 前端
├── e2e-tests/                   # Playwright 端到端测试
├── docker/                      # 各环境 Docker Compose 配置
│   ├── dev/
│   ├── staging/
│   └── prod/
└── docs/                        # 设计文档与实施计划
    ├── ci-cd-setup.md
    └── superpowers/
        ├── specs/               # 模块设计规范（7份）
        └── plans/               # 实施计划（6份）
```

---

## 服务详情

### 后端 — ai-hiring-backend

**技术：** Spring Boot 3, Gradle, Hibernate/JPA, Flyway, JWT

**目录结构：**

```
ai-hiring-backend/
├── Dockerfile                   # 多阶段构建；非 root 用户 spring 运行；支持 JAVA_OPTS
├── build.gradle
├── settings.gradle
└── src/
    ├── main/
    │   ├── java/com/aihiring/
    │   │   ├── AiHiringApplication.java      # Spring Boot 入口
    │   │   ├── DataInitializer.java          # 启动时初始化 admin 账号和基础数据
    │   │   │
    │   │   ├── auth/                         # 认证模块
    │   │   │   ├── AuthController.java       # POST /api/auth/login|refresh|logout
    │   │   │   ├── AuthService.java          # 登录逻辑、token 发放
    │   │   │   ├── RefreshToken.java         # refresh token 实体
    │   │   │   ├── RefreshTokenRepository.java
    │   │   │   └── dto/
    │   │   │       ├── LoginRequest.java
    │   │   │       ├── LogoutRequest.java
    │   │   │       ├── RefreshRequest.java
    │   │   │       └── TokenResponse.java    # accessToken + refreshToken
    │   │   │
    │   │   ├── common/                       # 共享基础设施
    │   │   │   ├── config/
    │   │   │   │   ├── CorsConfig.java       # CORS 允许所有来源（开发用）
    │   │   │   │   └── SecurityConfig.java   # Spring Security 配置、白名单路径
    │   │   │   ├── dto/
    │   │   │   │   └── ApiResponse.java      # 统一响应体 {code, message, data}
    │   │   │   ├── entity/
    │   │   │   │   └── BaseEntity.java       # id, createdAt, updatedAt
    │   │   │   ├── exception/
    │   │   │   │   ├── BusinessException.java
    │   │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   │   └── ResourceNotFoundException.java
    │   │   │   └── security/
    │   │   │       ├── JwtAuthFilter.java    # JWT 请求拦截器
    │   │   │       ├── JwtTokenProvider.java # token 生成、验证、解析
    │   │   │       └── UserDetailsImpl.java
    │   │   │
    │   │   ├── user/                         # 用户管理
    │   │   │   ├── User.java                 # 用户实体
    │   │   │   ├── UserController.java       # GET /api/users/me, /api/users
    │   │   │   ├── UserRepository.java
    │   │   │   ├── UserService.java
    │   │   │   └── dto/
    │   │   │       └── UserResponse.java
    │   │   │
    │   │   ├── role/                         # RBAC 角色权限
    │   │   │   ├── Permission.java
    │   │   │   ├── Role.java
    │   │   │   ├── RoleRepository.java
    │   │   │   └── dto/
    │   │   │
    │   │   ├── department/                   # 部门管理
    │   │   │   ├── Department.java
    │   │   │   ├── DepartmentController.java # GET|POST|PUT|DELETE /api/departments
    │   │   │   ├── DepartmentRepository.java
    │   │   │   ├── DepartmentService.java
    │   │   │   └── dto/
    │   │   │       ├── CreateDepartmentRequest.java
    │   │   │       ├── DepartmentResponse.java
    │   │   │       └── UpdateDepartmentRequest.java
    │   │   │
    │   │   ├── job/                          # 职位描述管理
    │   │   │   ├── JobDescription.java       # 实体；skills 用 @JdbcTypeCode(JSONB)；status 用 PostgreSQLEnumJdbcType
    │   │   │   ├── JobStatus.java            # enum: DRAFT|PUBLISHED|CLOSED
    │   │   │   ├── JobController.java        # GET|POST|PUT|DELETE /api/jobs
    │   │   │   ├── JobRepository.java
    │   │   │   ├── JobService.java
    │   │   │   ├── JobDescriptionSavedEvent.java  # 发布事件触发向量化
    │   │   │   └── dto/
    │   │   │       ├── ChangeStatusRequest.java
    │   │   │       ├── CreateJobRequest.java
    │   │   │       ├── JobListResponse.java
    │   │   │       ├── JobResponse.java
    │   │   │       └── UpdateJobRequest.java
    │   │   │
    │   │   ├── resume/                       # 简历管理（核心模块）
    │   │   │   ├── Resume.java               # 实体；education/experience/projects/skills 用 @JdbcTypeCode(JSONB)；source/status 用 PostgreSQLEnumJdbcType
    │   │   │   ├── ResumeSource.java         # enum: MANUAL|BOSS
    │   │   │   ├── ResumeStatus.java         # enum: UPLOADED|TEXT_EXTRACTED|AI_PARSED|VECTORIZED|FAILED
    │   │   │   ├── ResumeController.java     # POST /api/resumes/upload; GET /api/resumes; GET /api/resumes/{id}
    │   │   │   ├── ResumeRepository.java
    │   │   │   ├── ResumeService.java        # 上传、文本提取、保存
    │   │   │   ├── ResumeUploadedEvent.java  # 发布事件触发 AI 解析
    │   │   │   ├── PostgresResumeSearchRepository.java
    │   │   │   └── parser/
    │   │   │       ├── TextExtractor.java    # 接口
    │   │   │       ├── PdfTextExtractor.java # Apache PDFBox
    │   │   │       ├── DocxTextExtractor.java# Apache POI
    │   │   │       └── TxtTextExtractor.java
    │   │   │
    │   │   └── matching/                     # AI 匹配集成
    │   │       ├── AiMatchingClient.java     # HTTP 客户端 → AI 服务
    │   │       ├── AiMatchingEventListener.java # 监听 ResumeUploaded/JDSaved 事件
    │   │       ├── MatchController.java      # POST /api/match
    │   │       ├── MatchService.java
    │   │       └── dto/
    │   │           ├── AiMatchRequest.java
    │   │           ├── AiMatchResponse.java
    │   │           ├── AiMatchResultItem.java
    │   │           ├── MatchRequest.java
    │   │           ├── MatchResponse.java
    │   │           ├── MatchResultItem.java
    │   │           ├── VectorizeJobRequest.java
    │   │           └── VectorizeResumeRequest.java
    │   │
    │   └── resources/
    │       ├── application.yml              # 基础配置（JWT secret, AI 服务 URL）
    │       ├── application-dev.yml          # 开发环境（本地 PostgreSQL, MinIO）
    │       ├── application-staging.yml      # Staging 环境
    │       └── db/migration/               # Flyway 迁移脚本（按版本顺序执行）
    │           ├── V1__initial_schema.sql   # users, roles, permissions, departments 表
    │           ├── V2__seed_data.sql        # 初始角色和权限数据
    │           ├── V3__resume_table.sql     # resumes 表（含 JSONB 字段 + PG ENUM）
    │           ├── V4__job_description_table.sql # job_descriptions 表
    │           └── V5__add_updated_at_to_permissions.sql
    │
    └── test/
        └── java/com/aihiring/    # 48 个测试（单元 + 集成，覆盖所有模块）
```

**关键 Hibernate 注解（踩坑记录）：**

| 字段类型 | 所需注解 | 原因 |
|----------|----------|------|
| PostgreSQL JSONB | `@JdbcTypeCode(SqlTypes.JSON)` | Hibernate 不自动映射 String → JSONB |
| PostgreSQL native ENUM | `@JdbcType(PostgreSQLEnumJdbcType.class)` | Hibernate 默认映射字符串，非原生 ENUM |

---

### AI 匹配服务 — ai-matching-service

**技术：** Python 3.11, FastAPI, OpenAI Embeddings, Pydantic

```
ai-matching-service/
├── Dockerfile
├── requirements.txt
├── main.py                      # FastAPI 应用入口，注册路由
├── config.py                    # 环境变量配置（OPENAI_API_KEY, vector DB URL 等）
├── schemas.py                   # Pydantic 数据模型
├── routers/
│   ├── vectorize.py             # POST /vectorize/resume, /vectorize/job
│   └── match.py                 # POST /match — 给定 JD 返回匹配简历列表
├── services/
│   ├── embedding.py             # OpenAI text-embedding-ada-002 向量生成
│   ├── vector_store.py          # 向量数据库读写（Milvus/Qdrant 抽象层）
│   └── llm.py                   # GPT-4 匹配推理、生成评分和原因
└── tests/
    ├── conftest.py
    ├── test_health.py
    ├── test_embedding.py
    ├── test_vector_store.py
    ├── test_vectorize.py
    ├── test_match.py
    └── test_llm.py
```

---

### 前端 — frontend

**技术：** React 18, TypeScript, Vite, Ant Design, Axios

```
frontend/
├── Dockerfile                   # 多阶段：Node build → Nginx serve
├── package.json
├── vite.config.ts
├── tsconfig.json
└── src/
    ├── main.tsx                 # React 应用入口
    ├── App.tsx                  # 路由配置（React Router）
    ├── App.css / index.css
    ├── setupTests.ts            # Jest 测试配置
    │
    ├── api/                     # API 客户端层
    │   ├── request.ts           # Axios 封装：自动附加 Bearer token，处理 401 刷新
    │   ├── request.test.ts
    │   ├── types.ts             # 共享 TypeScript 类型定义
    │   ├── auth.ts              # login(), refresh(), logout()
    │   ├── auth.test.ts
    │   ├── jobs.ts              # listJobs(), getJob(), createJob(), updateJob(), changeStatus()
    │   ├── jobs.test.ts
    │   ├── resumes.ts           # listResumes(), uploadResume(), downloadResume()
    │   ├── resumes.test.ts
    │   ├── departments.ts       # listDepartments()
    │   ├── departments.test.ts
    │   ├── match.ts             # matchResumes()
    │   └── match.test.ts
    │
    ├── context/
    │   ├── AuthContext.tsx      # 全局认证状态；提供 user, login(), logout()
    │   └── AuthContext.test.tsx
    │
    ├── components/
    │   ├── AppLayout.tsx        # 带侧边导航栏的主布局
    │   ├── ProtectedRoute.tsx   # 路由守卫，未登录跳转 /login
    │   └── ProtectedRoute.test.tsx
    │
    └── pages/
        ├── login/
        │   ├── LoginPage.tsx    # 登录表单
        │   └── LoginPage.test.tsx
        ├── jobs/
        │   ├── JobListPage.tsx  # 职位列表，支持筛选和状态
        │   ├── JobListPage.test.tsx
        │   ├── JobDetailPage.tsx # 职位详情、编辑、状态变更、触发 AI 匹配
        │   ├── JobDetailPage.test.tsx
        │   ├── JobCreatePage.tsx # 创建职位表单（含部门选择）
        │   └── JobCreatePage.test.tsx
        └── resumes/
            ├── ResumeListPage.tsx   # 简历列表，支持搜索
            ├── ResumeListPage.test.tsx
            ├── ResumeUploadPage.tsx # 上传简历（PDF/DOCX/TXT）
            └── ResumeUploadPage.test.tsx
```

---

## 数据库迁移

Flyway 按版本顺序自动执行，路径 `ai-hiring-backend/src/main/resources/db/migration/`：

| 版本 | 文件 | 内容 |
|------|------|------|
| V1 | `V1__initial_schema.sql` | users, roles, permissions, departments, user_roles 表 |
| V2 | `V2__seed_data.sql` | 初始化 SUPER_ADMIN/HR_ADMIN/DEPT_ADMIN/USER 角色和权限 |
| V3 | `V3__resume_table.sql` | resumes 表（含 JSONB 字段：education/experience/projects/skills；PostgreSQL ENUM：resume_source, resume_status） |
| V4 | `V4__job_description_table.sql` | job_descriptions 表（含 JSONB：skills；PostgreSQL ENUM：job_status） |
| V5 | `V5__add_updated_at_to_permissions.sql` | permissions 表追加 updated_at 字段 |

---

## Docker & 多环境配置

### 仓库内（模板）

```
docker/
├── dev/
│   ├── docker-compose.yml       # 开发环境：所有服务，挂载本地代码
│   └── .env.example
├── staging/
│   ├── docker-compose.yml       # Staging：使用 GHCR 镜像，端口 3002/8082/8001
│   └── .env.example
└── prod/
    ├── docker-compose.yml       # 生产：使用 GHCR 镜像，端口 3010/8080/8000
    └── .env.example
```

### VPS 上的实际配置（非 git 管理）

```
/opt/ai-hiring/docker/
├── staging/
│   ├── docker-compose.yml       # 实际运行的 staging compose（含 JAVA_OPTS）
│   └── .env                     # 含真实密钥（OPENAI_API_KEY, DB 密码等）
└── prod/
    ├── docker-compose.yml
    └── .env
```

**`JAVA_OPTS` 配置（staging/prod backend）：**
```
JAVA_OPTS=-XX:TieredStopAtLevel=1 -Xms256m -Xmx512m
```
> `-XX:TieredStopAtLevel=1` 跳过 JIT 编译，将 JVM 启动时间从 300s+ 缩短至约 60s。

---

## GitHub Actions 工作流

### `.github/workflows/ci.yml`

**触发：** 被其他 workflow 通过 `uses:` 调用（可复用）

**内容：**
- Java 17 + Gradle — 编译并运行所有后端测试
- Python 3.11 — `pytest ai-matching-service/tests/`
- Node 20 — 前端 `npm run lint && npm run build`

---

### `.github/workflows/deploy-staging.yml`

**触发：** 推送到 `master` 分支

**阶段：**

```
push to master
  └─► ci (调用 ci.yml)
        └─► build-and-push (构建并推送 3 个 Docker 镜像到 GHCR)
              ├── ghcr.io/<owner>/ai-hiring-backend:<sha>
              ├── ghcr.io/<owner>/ai-matching-service:<sha>
              └── ghcr.io/<owner>/ai-hiring-frontend:<sha>
                    └─► deploy (SSH → VPS → deploy.sh staging <sha> $GHCR_TOKEN)
                          └─► verify
                                ├── smoke test (login / resume upload / jobs list)
                                ├── comment on linked GitHub issues
                                └── notify Claude Code (写入 /opt/ai-hiring/notifications/queue.jsonl)
```

**所需 Secrets：**
`VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `OPENAI_API_KEY`, `GHCR_TOKEN`, `GITHUB_TOKEN`（自动提供）

---

### `.github/workflows/deploy-dev.yml`

**触发：** 推送到 `dev` 分支（自动部署开发环境）

---

### `.github/workflows/deploy-prod.yml`

**触发：** 手动 `workflow_dispatch`（需输入 commit SHA）

**安全机制：** 生产部署必须手动触发，防止意外发布。

---

## VPS 部署脚本

所有脚本位于 `/opt/ai-hiring/scripts/`，由 GitHub Actions 或 cron 调用。

---

### `deploy.sh`

**调用方式：** `deploy.sh <env> <sha> <ghcr_token>`

**功能：**
1. 验证参数（env 必须为 staging|prod）
2. 用 `$GHCR_TOKEN` 执行 `docker login ghcr.io`（修复 pull 401 错误的关键）
3. 更新 `docker/staging/.env` 中的 `IMAGE_TAG=<sha>`
4. `docker compose pull` — 拉取新镜像
5. `docker compose up -d` — 滚动重启容器
6. **轮询健康检查**（最长等待 360s，每 10s 检查一次）直到后端响应
7. 调用 `health-check.sh <env>` 做最终验证

---

### `health-check.sh`

**调用方式：** `health-check.sh <env>`

**功能：** 发送 HTTP 请求验证后端 `/api/auth/login` 是否可访问；返回非零退出码表示失败。

---

### `rollback.sh`

**调用方式：** `rollback.sh <env> <previous_sha>`

**功能：** 将 `.env` 中的 `IMAGE_TAG` 改回上一个版本，重新执行 `docker compose up -d`，实现快速回滚。

---

### `watch-issues.sh`

**调用方式：** 每 2 分钟由 cron 触发（`*/2 * * * *`）

**功能：**
1. 使用 `gh issue list --label autofix --state open` 拉取 GitHub issue
2. 对比 `/opt/ai-hiring/processed-issues.txt`，过滤已处理的
3. 发现新 issue → 记录 ID → 后台启动 `autofix-issue.sh <N>`
4. 使用 lockfile（`/tmp/ai-hiring-watch.lock`）防止并发运行
5. 日志写入 `/opt/ai-hiring/logs/watch-issues.log`

---

### `autofix-issue.sh`

**调用方式：** `autofix-issue.sh <issue-number>`（由 watch-issues.sh 后台调用）

**功能：**
1. `gh issue view $N` 获取 issue 标题和正文
2. 从标题生成 branch slug（`fix/issue-N-slug`；中文标题自动 fallback 为 `fix`）
3. `git worktree add -b $BRANCH worktrees/issue-$N origin/master` 创建隔离工作树
4. 构建结构化 prompt 传给 `claude -p`：
   - 指示先读 CLAUDE.md
   - 最小改动原则、TDD
   - commit message 格式：`fix: <描述> (closes #N)`
   - **不要** push 或创建 PR（脚本负责）
5. `claude -p "$PROMPT" --allowedTools "Bash,Read,Edit,Write,Glob,Grep" --max-turns 40`
6. 检查是否有新 commit；无则退出
7. `git push origin $BRANCH`
8. `gh pr create --base master --head $BRANCH --title "fix: $TITLE (closes #N)"`
9. `gh issue comment $N` 发 PR 链接通知
10. 日志写入 `/opt/ai-hiring/logs/autofix-$N.log`

---

### `check-notifications.sh`

**调用方式：** Claude Code `Stop` hook（`asyncRewake: true`）自动调用

**功能：**
1. 读取 `/opt/ai-hiring/notifications/queue.jsonl` 中第一条未读通知
2. 将其标记为已读
3. 输出 JSON（`systemMessage` + `hookSpecificOutput.additionalContext`）
4. 退出码 2 = 有新通知（触发 Claude Code 唤醒）；退出码 0 = 无待处理通知

**Claude Code 配置（`~/.claude/settings.json`）：**
```json
"hooks": {
  "Stop": [{
    "hooks": [{
      "type": "command",
      "command": "/opt/ai-hiring/scripts/check-notifications.sh",
      "asyncRewake": true,
      "timeout": 10,
      "statusMessage": "Checking deployment notifications..."
    }]
  }]
}
```

---

## 自动化 Issue → PR → Staging 工作流

```
[开发者] 创建 GitHub Issue，打上 "autofix" 标签
    ↓ (最多 2 分钟)
watch-issues.sh cron 检测到新 issue
    ↓
autofix-issue.sh #N
    ├── git worktree: fix/issue-N-slug
    ├── claude -p → 读代码 → 最小修复 → 跑测试 → commit
    ├── git push origin fix/issue-N-slug
    └── gh pr create → "fix: <title> (closes #N)"
              └── gh issue comment #N (附 PR 链接)
    ↓
[开发者] 审查并 Merge PR → master
    ↓
GitHub Actions: deploy-staging.yml 自动触发
    ├── ci.yml: 全量测试
    ├── build-and-push: 构建 Docker 镜像 → 推送 GHCR
    └── deploy: SSH → VPS
              ├── docker login ghcr.io
              ├── IMAGE_TAG 更新到 .env
              ├── docker compose pull && up -d
              └── 轮询健康检查（最长 360s）
    ↓
verify job:
    ├── smoke test (login / upload / jobs)
    ├── gh issue comment #N (测试结果)
    └── 写入 /opt/ai-hiring/notifications/queue.jsonl
    ↓
Claude Code Stop hook (check-notifications.sh)
    └── 唤醒 Claude Code → 用户可在 Claude Code 中看到部署结果
```

---

## 端口规划

| 服务 | Dev | Staging | Prod |
|------|-----|---------|------|
| Frontend | 3000 | 3002 | 3010 |
| Backend | 8080 | 8082 | 8080 |
| AI Service | 8000 | 8001 | 8000 |
| PostgreSQL | 5432 | 5432 (共享) | 5432 (共享) |
| MinIO API | 9000 | - | 9000 |
| MinIO Console | 9001 | - | 9001 |
| Nginx (外部) | - | - | 80 → :3010 |

> Nginx `:80` 反代生产前端 `:3010`；`/api/` 反代后端 `:8080`

---

## 文档 & 规范

### 设计规范（`docs/superpowers/specs/`）

| 文件 | 内容 |
|------|------|
| `2026-03-14-recruitment-platform-design.md` | 全平台系统设计：角色权限、简历解析流水线、AI 匹配流程、数据模型、API 合约 |
| `2026-03-16-user-auth-module-design.md` | 用户认证模块设计（JWT 双 token） |
| `2026-03-18-resume-module-design.md` | 简历模块设计（上传 → 提取 → AI 解析 → 向量化） |
| `2026-03-18-jd-module-design.md` | 职位描述模块设计 |
| `2026-03-18-ai-matching-service-design.md` | AI 匹配服务设计（向量检索 + LLM 推理） |
| `2026-03-19-frontend-design.md` | 前端设计（页面结构、API 集成、组件设计） |
| `2026-03-22-ci-cd-design.md` | CI/CD 流水线设计 |

### 实施计划（`docs/superpowers/plans/`）

| 文件 | 内容 |
|------|------|
| `2026-03-17-user-auth-module.md` | 用户认证模块实施步骤 |
| `2026-03-18-resume-module.md` | 简历模块实施步骤 |
| `2026-03-18-jd-module.md` | JD 模块实施步骤 |
| `2026-03-18-ai-matching-service.md` | AI 服务实施步骤 |
| `2026-03-19-frontend-mvp.md` | 前端 MVP 实施步骤 |
| `2026-03-22-ci-cd-implementation.md` | CI/CD 实施步骤 |

### 其他

- `docs/ci-cd-setup.md` — CI/CD 搭建指南（GitHub Secrets 配置、VPS 初始化）
- `CLAUDE.md` — Claude Code 工作方法论（计划模式、TDD、子 Agent 策略）

---

## E2E 测试

```
e2e-tests/
├── playwright.config.ts         # 配置：baseURL, 超时, 截图
├── package.json
├── fixtures/
│   ├── sample-resume.pdf        # 测试用简历
│   └── sample-jd.txt            # 测试用职位描述
└── tests/
    ├── setup.ts                 # 全局 setup：创建测试用户
    ├── auth.spec.ts             # 登录/登出/token 刷新流程
    ├── resume.spec.ts           # 简历上传、列表、解析状态
    ├── job.spec.ts              # 职位创建、编辑、状态变更
    └── match.spec.ts            # AI 匹配触发与结果展示
```

---

*本文件由 Claude Code 生成，反映截止 2026-03-28 的项目状态。*
