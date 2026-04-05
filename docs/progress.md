# Progress

> 最后更新：2026-04-05
>
> 供下一个 agent 接手时快速建立上下文。先读本文件，再读 CLAUDE.md，再看 PROJECT_MAP.md。

---

## 项目概况

AI 招聘平台（SaaS），统一简历管理（Boss 直聘集成 + 手动上传）+ 智能 JD-简历匹配。

**三服务架构**：
1. **Core Business Service** (Spring Boot / Java) — 端口 8080/8081/8082
2. **AI Matching Service** (Python / FastAPI) — 端口 8000/8001/8002
3. **Frontend** (React + Ant Design) — 端口 3010/3001/3002

**VPS**: 184.32.94.23

---

## 环境与端口映射

| 环境 | 后端 | AI 服务 | 前端 | 触发方式 | 分支 |
|------|------|---------|------|----------|------|
| dev | :8081 | :8001 | :3001 | push feature/* | feature/* |
| staging | :8082 | :8002 | :3002 | push master | master |
| prod | :8080 | :8000 | :3010 | 手动 workflow_dispatch | master |

---

## 已完成模块

### 后端（Spring Boot）

| 模块 | 状态 | 说明 |
|------|------|------|
| Auth | ✅ 完成 | JWT 双 token（access 2h + refresh 7d），token 轮换，logout 吊销 |
| User | ✅ 完成 | `/api/users/me`，用户列表，RBAC 角色绑定 |
| Department | ✅ 完成 | CRUD，树形层级，seed 3 个部门（总部/技术/人事） |
| Role & Permission | ✅ 完成 | 4 角色 + 12 权限，DataInitializer 启动时写入 |
| Job Description | ✅ 完成 | CRUD，状态流转（DRAFT→PUBLISHED→CLOSED） |
| Resume | ✅ 完成 | 上传（PDF/DOCX/TXT），文本提取，状态流转 |
| Resume Batch Upload | ✅ 完成 | 多文件批量上传，10MB 大小验证 |
| AI Matching | ✅ 完成（集成层） | MatchController/Service/Client，事件监听异步触发向量化 |
| Resume Status 闭环 | ✅ 完成 | UPLOADED → TEXT_EXTRACTED → AI_PROCESSED / VECTORIZATION_FAILED |

### AI 匹配服务（Python / FastAPI）

| 组件 | 状态 | 说明 |
|------|------|------|
| Embedding | ✅ 真实 | `litellm` + `text-embedding-3-small`，1536 维 |
| Vector Store | ✅ 真实 | Qdrant，cosine 距离 |
| LLM Scoring | ✅ 真实 | `gpt-4o-mini`，返回 0-100 分 + 推理 + highlights |
| 向量化端点 | ✅ 真实 | `POST /internal/vectorize/resume` + `/internal/vectorize/job` |
| 匹配端点 | ✅ 真实 | `POST /match`，两阶段：向量检索 → LLM 重排序 |

### 前端（React + Ant Design）

| 页面 | 状态 | 说明 |
|------|------|------|
| LoginPage | ✅ 完成 | 真实 API，token 存储，refresh 自动恢复会话 |
| JobListPage | ✅ 完成 | 真实 API，分页，状态筛选 |
| JobDetailPage | ✅ 完成 | 真实 API，编辑、状态变更、触发 AI 匹配 |
| JobCreatePage | ✅ 完成 | 真实 API，部门下拉真实数据 |
| ResumeListPage | ✅ 完成 | 真实 API，分页，状态 badge |
| ResumeUploadPage | ✅ 完成 | 真实 API，上传 PDF/DOCX/TXT |
| BatchUploadModal | ✅ 完成 | 批量上传组件，集成到 ResumeListPage |

---

## CI/CD 自动化流程（2026-04-04 补齐）

### 自动化流程目标

完整流程定义：`docs/diagrams/auto-workflow.mmd`

```
Issue 创建 → 自动标签分诊 → 分支开发 → PR → AI Code Review → CI 通过 → 合并 →
Staging 部署 → Smoke Test → E2E 测试 → (失败自动建 Issue) →
Production 手动部署 → (失败自动回滚 + 告警)
```

### 已实现的自动化组件

| 组件 | 文件 | 状态 | 说明 |
|------|------|------|------|
| Issue 模板 | `.github/ISSUE_TEMPLATE/bug_report.yml` | ✅ | YAML 表单，自动标 `bug` label |
| Issue 模板 | `.github/ISSUE_TEMPLATE/feature_request.yml` | ✅ | YAML 表单，自动标 `feature` label |
| Issue 配置 | `.github/ISSUE_TEMPLATE/config.yml` | ✅ | 禁用空白 issue |
| Issue 自动分诊 | `.github/workflows/issue-triage.yml` | ✅ | 关键词扫描自动打模块标签（auth/resume/job/ai-service/ui/infra/boss-integration） |
| PR 模板 | `.github/PULL_REQUEST_TEMPLATE.md` | ✅ | Summary/Changes/Issues/Test Plan/Checklist |
| AI Code Review | `.github/workflows/ai-code-review.yml` | ✅ | PR opened/synchronize 时调用 OpenAI gpt-4o，审查安全/架构/代码质量，发评论 |
| CI | `.github/workflows/ci.yml` | ✅ | Java 测试 + Python pytest + 前端 lint/build |
| Dev 部署 | `.github/workflows/deploy-dev.yml` | ✅ | push feature/* 自动部署 + E2E + 失败建 Issue |
| Staging 部署 | `.github/workflows/deploy-staging.yml` | ✅ | push master 自动部署 + Smoke Test + E2E + 失败建 Issue |
| Prod 部署 | `.github/workflows/deploy-prod.yml` | ✅ | 手动触发，含自动回滚 + 失败告警 Issue |
| verify-issue.sh | `scripts/verify-issue.sh` | ✅ | 接收 (issue_number, base_url, issue_body)，跑 auth+jobs+resume+AI health check |
| verify-staging.sh | `scripts/verify-staging.sh` | ✅ | 真实数据端到端验证（JD 创建 + 简历上传 + 向量化 + AI 匹配） |
| Branch 保护 | `scripts/setup-branch-protection.sh` | ✅ | CI 必须通过，enforce_admins=true，required_approving_review_count=0（允许 self-review） |
| E2E 测试 | `e2e-tests/` | ✅ | 10/10 通过（前端语义化导航修复后） |
| 真实测试数据 | `trainingdata/` + `scripts/test-data/` | ✅ | 2 份简历 PDF + 1 份 JD PDF + 结构化 JD JSON |

### GitHub Secrets 清单

| Secret | 用途 | 状态 |
|--------|------|------|
| `VPS_HOST` | VPS IP | 已设置 |
| `VPS_USER` | SSH 用户 | 已设置 |
| `VPS_SSH_KEY` | SSH 私钥 | 已设置 |
| `GHCR_PAT` | GHCR 容器 registry | 已设置 |
| `OPENAI_API_KEY` | AI Code Review + AI 服务 | 已设置 |
| `TEST_USERNAME` | E2E 测试账号 | 已设置（admin） |
| `TEST_PASSWORD` | E2E 测试密码 | 已设置（admin123） |
| `GITHUB_TOKEN` | 自动提供，无需配置 | ✅ |

### Branch 保护规则（master）

- `enforce_admins: true` — 管理员也必须走 PR 流程
- `required_status_checks`: CI (backend, ai-service, frontend) 必须通过
- `required_approving_review_count: 0` — CI 通过即可合并，允许 self-review
- 设置脚本：`scripts/setup-branch-protection.sh`

---

## E2E 测试状态

### Playwright UI 测试（10/10 通过）

通过前端语义化 `<Link>` 修复（PR #41），所有 Playwright 测试通过：
- `auth.setup.ts` ✅, `auth.spec.ts` ✅
- `job.spec.ts` ✅ (4 tests), `resume.spec.ts` ✅ (3 tests)
- `match.spec.ts` ✅

### 真实数据端到端验证（scripts/verify-staging.sh）

使用 `trainingdata/` 目录下的真实简历和 JD 验证完整业务流程：

| 步骤 | 状态 | 说明 |
|------|------|------|
| Auth 登录 | ✅ | |
| 获取部门 | ❌ | API 返回格式问题 |
| 创建 JD | ❌ | 依赖部门 |
| 上传简历 (覃启航.pdf) | ✅ | |
| 上传简历 (张芷菁.pdf) | ✅ | |
| **简历向量化** | ❌ | 卡在 TEXT_EXTRACTED，不转为 AI_PROCESSED |
| AI 匹配 | ⏭️ | 被向量化阻塞 |

**核心发现**：staging 的简历向量化管线有问题 — 简历文本提取成功但无法推进到向量化步骤。

### Ant Design 选择器注意事项（给下一个 agent）

1. **Form.Item 的 `name` prop 不会设到 `<input>` 上** — 用 `getByPlaceholder()`
2. **Menu 必须用 React Router `<Link>`** — 否则 `getByRole('link')` 不匹配
3. **Select.Option 在 Ant Design v6 中废弃** — 必须用 `options` prop
4. **Alert 组件** — 用 `.ant-alert-error` 等类名选择器

---

## Claude Code 自动化流程（2026-04-05 建立）

### 架构：GitHub Actions + Claude Code 协同

**GitHub Actions 负责**（需要 Runner 和事件触发）：
- CI / Build / Deploy / AI Code Review / Issue Triage

**Claude Code Session 负责**（CronCreate + Subagent）：
- PR 合并监控（每 3 分钟）
- autofix label issue 自动修复（subagent 处理）
- Staging 部署结果监控
- Production 部署触发（人工确认后）

### 核心规则

1. **Staging → Production 门禁**：Staging 部署后必须通过 Smoke + E2E，Production 不自动触发
2. **autofix issue**：带 `autofix` label 的 issue 自动启动 subagent 修复 → 创建 PR → 等用户 review
3. **创建 PR 前必须 rebase**：`git fetch origin && git rebase origin/master`
4. **每次回复时先调用 Skill**：检查是否有适用的 skill

### 当前 Cron 任务

| 任务 | 频率 | 行为 |
|------|------|------|
| PR 合并检测 | 每 3 分钟 | 报告 staging 状态，不自动触发 prod |
| autofix issue 检测 | 每 3 分钟 | 发现 autofix label → subagent 修复 → 创建 PR |

### 真实测试数据

```
trainingdata/
  覃启航.pdf                    — 候选人简历（Unity 开发经验）
  张芷菁.pdf                    — 候选人简历（机器学习/Unity 方向）
  Unity 中高级开发工程师.pdf      — JD（XR + Unity + C#）

scripts/test-data/
  jd-unity.json                 — 结构化 JD 数据（从 PDF 提取）
```

---

## Staging 最近一次部署结果

**Run ID**: 23991016083 (PR #50 合并后)

| 阶段 | 结果 |
|------|------|
| CI | ✅ 全部通过 |
| Build + Push | ✅ |
| Deploy | ✅ |
| Smoke Test (verify) | ✅ |
| E2E | ✅ 10/10 通过 |

---

## Production 部署方式

Production 不自动部署，需要手动触发：

1. 进入 GitHub → Actions → "Deploy to Production"
2. 点击 "Run workflow"
3. 输入要部署的 commit SHA
4. 确认运行

失败时会自动：
- 回滚到上一个版本（从 VPS .env 读取 PREV_VERSION）
- 创建告警 Issue

---

## 当前未解决问题

### P1 — Staging 向量化管线故障

**真实数据验证发现**：简历上传后文本提取成功（TEXT_EXTRACTED），但无法推进到 AI 向量化（AI_PROCESSED）。
- 一份简历变为 VECTORIZATION_FAILED，其余卡在 TEXT_EXTRACTED
- 可能原因：AI 服务到 Qdrant 的连接问题，或 OPENAI_API_KEY 配置问题
- 验证脚本：`scripts/verify-staging.sh http://184.32.94.23:8082 trainingdata/`

### P2 — 功能未实现

**Boss 直聘集成**：
- `ResumeSource.BOSS` 枚举值存在，但无 API 客户端、OAuth 流程或同步逻辑
- 相关 API 端点在 CLAUDE.md 中定义但未实现

### P3 — JD 创建 API 问题

**真实数据验证发现**：通过 API 创建 JD 时 departments 接口返回格式不匹配，导致无法创建真实 JD 进行匹配测试。

---

## 下一个 Agent 接手指南

### 第 1 步 — 读这三个文件

1. `docs/progress.md`（本文件）— 当前状态
2. `CLAUDE.md` — 开发方法论、完成定义、bug 修复规则
3. `PROJECT_MAP.md` — 完整文件地图和架构

### 第 2 步 — 确认 staging 是否在线

```bash
curl -s http://184.32.94.23:8082/api/auth/login \
  -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 第 3 步 — 优先处理顺序

| 优先级 | 问题 | 说明 |
|--------|------|------|
| P1 | 排查 Staging 向量化管线 | 简历卡在 TEXT_EXTRACTED，不转为 AI_PROCESSED |
| P2 | Boss 直聘集成 | 大功能，需要设计 OAuth 流程 |
| P3 | JD 创建 API 接口修复 | departments 返回格式不匹配 |
| P3 | Production 首次完整验证 | 用真实数据验证完整流程 |

### 第 4 步 — 工作流程

1. 创建 feature 分支：`git checkout -b feature/xxx`
2. 开发 + 本地测试
3. Push + 创建 PR → AI Code Review 自动触发 + CI 自动运行
4. PR 被 review（可以 self-review）后合并到 master
5. 合并自动触发 staging 部署 → Smoke + E2E
6. Staging 验证通过后手动触发 prod 部署

### 关键路径依赖

```
简历上传正常工作需要：
  PostgreSQL ← Flyway 迁移 + DataInitializer
  MinIO (文件存储)
  Spring Boot 以 spring 用户运行（非 root）且 /app/uploads 有写权限

AI 匹配正常工作需要：
  上述所有 + Qdrant（向量 DB）+ OPENAI_API_KEY 环境变量
```

### 已知配置文件位置

- VPS 实际配置（含密钥）：`/opt/ai-hiring/docker/staging/.env`
- 部署脚本：`/opt/ai-hiring/scripts/`
- 通知队列：`/opt/ai-hiring/notifications/queue.jsonl`
- 自动修复日志：`/opt/ai-hiring/logs/autofix-<N>.log`

### 处理 Issue 时的规则

- 打了 `autofix` label 的 issue：`watch-issues.sh` 会自动检测并触发 `autofix-issue.sh`
- 手动处理时：严格遵守 `docs/issue-fixes-rules.md` 的验证规则
- **不要仅凭代码编译/测试通过就声称修复** — 必须包含 DB/API/UI 验证证据

---

## 已解决的 P1 问题

### ✅ Resume Status 枚举对齐（2026-04-01）
- 前端 STATUS_COLORS / STATUS_LABELS 完全匹配后端 ResumeStatus 枚举
- commit: `8fbf37e`

### ✅ 向量化状态闭环（2026-04-01）
- AiMatchingClient.vectorizeResume() 返回 boolean
- 成功 → AI_PROCESSED，失败 → VECTORIZATION_FAILED
- commit: `6e8e599`

### ✅ CI/CD 自动化流程补齐（2026-04-04）
- 7 项缺失自动化全部实现（Issue 模板/分诊、AI Code Review、verify-issue.sh、失败建 Issue、Prod 回滚、Branch 保护、PR 模板 + Staging E2E）
- 通过 PR #29, #31, #37 合并

### ✅ E2E 登录认证修复（2026-04-04）
- 修正 Ant Design 选择器（getByPlaceholder 代替 input[name]）
- 正确配置 TEST_USERNAME/TEST_PASSWORD secrets
- 3/10 E2E 测试通过（auth.setup + 登录成功 + 登录失败）

### ✅ 前端语义化导航修复（2026-04-05）
- Menu 的 `onClick + navigate()` 改为 React Router `<Link>`，渲染 `<a>` 标签
- E2E 测试从 3/10 提升到 10/10
- PR #41

### ✅ E2E 测试条件判断修复（2026-04-05）
- `match.spec.ts`/`job.spec.ts` 改用 `isVisible({ timeout }).catch(() => false)` 处理空表格
- PR #43

### ✅ Production 部署修复（2026-04-05）
- 修复 `$GITHUB_OUTPUT` 在 SSH 会话不可用的问题（用 `capture_stdout` + `head -1`）
- Production 首次成功部署
- PR #45, #47

### ✅ Issue #49 自动修复（2026-04-05）
- 问题：JD 创建 submit 按钮无反应
- 原因：Ant Design v6 中 `Select.Option` 渲染为 null
- 修复：改用 `options` prop
- PR #50（通过 autofix cron + subagent 自动完成）

### ✅ 真实数据 E2E 验证（2026-04-05）
- 创建 `scripts/verify-staging.sh` 用真实简历和 JD 做端到端验证
- 发现 staging 向量化管线问题（简历卡在 TEXT_EXTRACTED）
- PR #54
