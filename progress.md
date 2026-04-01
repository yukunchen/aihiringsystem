# Progress

> 最后更新：2026-04-01
>
> 供下一个 agent 接手时快速建立上下文。先读本文件，再读 CLAUDE.md，再看 PROJECT_MAP.md。

---

## 已完成模块

### 后端（Spring Boot）

| 模块 | 状态 | 说明 |
|------|------|------|
| Auth | ✅ 完成 | JWT 双 token（access 2h + refresh 7d），token 轮换，logout 吊销 |
| User | ✅ 完成 | `/api/users/me`，用户列表，RBAC 角色绑定 |
| Department | ✅ 完成 | CRUD，树形层级，seed 3 个部门（总部/技术/人事） |
| Role & Permission | ✅ 完成 | 4 角色 + 12 权限，DataInitializer 启动时写入 |
| Job Description | ✅ 完成 | CRUD，状态流转（DRAFT→PUBLISHED→CLOSED），`@JdbcTypeCode(JSON)` + `PostgreSQLEnumJdbcType` 已修复 |
| Resume | ✅ 完成 | 上传（PDF/DOCX/TXT），文本提取，状态流转，`@JdbcTypeCode(JSON)` + `PostgreSQLEnumJdbcType` 已修复，`/app/uploads` 目录权限已修复 |
| Resume Batch Upload | ✅ 完成 | 多文件批量上传，10MB 大小验证，BatchUploadModal 前端组件 |
| AI Matching | ✅ 完成（集成层） | MatchController/Service/Client，事件监听异步触发向量化；向量化成功→AI_PROCESSED，失败→VECTORIZATION_FAILED |
| Resume Status 闭环 | ✅ 完成 | ResumeStatus 枚举：UPLOADED → TEXT_EXTRACTED → AI_PROCESSED / VECTORIZATION_FAILED |

### AI 匹配服务（Python / FastAPI）

| 组件 | 状态 | 说明 |
|------|------|------|
| Embedding | ✅ 真实 | `litellm` + `text-embedding-3-small`，1536 维 |
| Vector Store | ✅ 真实 | Qdrant，cosine 距离，collections: `resumes` / `jobs` |
| LLM Scoring | ✅ 真实 | `gpt-4o-mini`，返回 0-100 分 + 推理 + highlights |
| 向量化端点 | ✅ 真实 | `POST /internal/vectorize/resume` + `/internal/vectorize/job` |
| 匹配端点 | ✅ 真实 | `POST /match`，两阶段：向量检索 → LLM 重排序 |

### 前端（React）

| 页面 | 状态 | 说明 |
|------|------|------|
| LoginPage | ✅ 完成 | 真实 API，token 存储，refresh 自动恢复会话 |
| JobListPage | ✅ 完成 | 真实 API，分页，状态筛选 |
| JobDetailPage | ✅ 完成 | 真实 API，编辑、状态变更、触发 AI 匹配 |
| JobCreatePage | ✅ 完成 | 真实 API，部门下拉真实数据 |
| ResumeListPage | ✅ 完成 | 真实 API，分页，状态 badge（含 VECTORIZATION_FAILED 红色标签） |
| ResumeUploadPage | ✅ 完成 | 真实 API，上传 PDF/DOCX/TXT |
| BatchUploadModal | ✅ 完成 | 批量上传组件，集成到 ResumeListPage |

### CI/CD & 自动化

| 组件 | 状态 | 说明 |
|------|------|------|
| ci.yml | ✅ 完成 | Java 测试 + Python pytest + 前端 lint/build |
| deploy-staging.yml | ✅ 完成 | push master 自动触发，含 smoke test + issue 评论 + Claude Code 通知 |
| deploy-prod.yml | ✅ 完成 | 手动 workflow_dispatch，需输入 SHA |
| deploy.sh | ✅ 完成 | GHCR 登录、IMAGE_TAG 更新、轮询健康检查（360s），已修复 401/timeout 问题 |
| watch-issues.sh | ✅ 完成 | cron 每 2 分钟轮询 `autofix` label issue |
| autofix-issue.sh | ✅ 完成 | `claude -p` 自动修复 → git worktree → PR 创建 |
| check-notifications.sh | ✅ 完成 | Stop hook asyncRewake，部署完成后唤醒 Claude Code |

---

## 已解决的 P1 问题

### ✅ Resume Status 枚举对齐（2026-04-01）
- 前端 STATUS_COLORS / STATUS_LABELS 现在完全匹配后端 ResumeStatus 枚举
- UPLOADED → default/Uploaded, TEXT_EXTRACTED → processing/Text Extracted, AI_PROCESSED → success/AI Processed, VECTORIZATION_FAILED → error/Vectorization Failed
- commit: `8fbf37e`

### ✅ 向量化状态闭环（2026-04-01）
- AiMatchingClient.vectorizeResume() 返回 boolean（成功/失败）
- AiMatchingEventListener 监听结果，更新 resume status：
  - 成功 → AI_PROCESSED
  - 失败 → VECTORIZATION_FAILED（新增枚举值）
- Flyway V6 迁移：`ALTER TYPE resume_status ADD VALUE 'VECTORIZATION_FAILED'`
- 前端新增 error 红色 badge
- commit: `6e8e599`

---

## 当前未解决问题

### P2 — 功能未实现

#### Boss 直聘集成
- `ResumeSource.BOSS` 枚举值存在，但无任何 API 客户端、OAuth 流程或同步逻辑
- 相关 API 端点（`/api/boss/auth-url`, `/api/boss/callback`, `/api/boss/sync`）在 CLAUDE.md 中定义但未实现

### P3 — 验证/运维

#### Staging 环境 Qdrant 未确认
- Qdrant 在 staging docker-compose 中是否运行未验证
- AI 服务向量化在 staging 是否真实成功未经端到端测试

---

## 哪些页面仍是 Mock

**无 mock 页面。** 所有前端页面均调用真实 API。

---

## 哪些接口已真实联调（staging 验证通过）

以下接口经 staging smoke test 或手动 curl 验证（2026-03-28/29）：

| 接口 | 方法 | 验证方式 | 结果 |
|------|------|----------|------|
| `/api/auth/login` | POST | staging curl | ✅ 返回 accessToken |
| `/api/resumes/upload` | POST | staging curl | ✅ `{"code":200,"status":"TEXT_EXTRACTED"}` |
| `/api/jobs` | GET | staging smoke test | ✅ `{"code":200}` |
| `/api/departments` | GET | 前端 JobCreatePage 联调 | ✅ 返回 3 个部门 |

---

## 下一个 Agent 接手时先看什么

### 第 1 步 — 读这三个文件
1. `progress.md`（本文件）— 当前状态
2. `CLAUDE.md` — 开发方法论、完成定义、bug 修复规则
3. `PROJECT_MAP.md` — 完整文件地图和架构

### 第 2 步 — 确认当前 staging 状态
```bash
curl -s http://<VPS_HOST>:8082/api/auth/login \
  -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 第 3 步 — 优先处理顺序

| 优先级 | 问题 | 工作量估计 |
|--------|------|-----------|
| P2 | Boss 直聘集成 | 大，完整新功能 |
| P3 | Staging E2E 验证（Qdrant + 向量化 + 匹配） | 中，需 VPS 访问 |

### 第 4 步 — 处理新 Issue 时的规则
- 打了 `autofix` label 的 issue：`watch-issues.sh` 会自动检测并触发 `autofix-issue.sh`
- 手动处理时：严格遵守 `docs/issue-fixes-rules.md` 的验证规则
- **不要仅凭代码编译/测试通过就声称修复** — 必须包含 DB/API/UI 验证证据

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
