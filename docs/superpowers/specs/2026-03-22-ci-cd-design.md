# CI/CD 功能设计文档

## 概述

为 AI 招聘平台项目建立完整的 CI/CD 流水线，支持三环境部署（Dev、Staging、Production），使用 GitHub Actions 作为 CI 平台，部署到 Amazon Lightsail VPS。

## 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                        GitHub Repository                         │
│  feature/*  →  master  →  release-v* (manual trigger)           │
└──────────────┬──────────────┬──────────────┬────────────────────┘
               │              │              │ (manual)
               ▼              ▼              ▼
        ┌──────────┐   ┌──────────┐   ┌──────────┐
        │ CI + Dev │   │   CI +   │   │   CI +   │
        │  Deploy  │   │ Staging  │   │   Prod   │
        └────┬─────┘   └────┬─────┘   └────┬─────┘
             │              │              │
             ▼              ▼              ▼
     dev.xxx.com     staging.xxx.com      xxx.com
        :3001            :3002            :3000
        :8081            :8082            :8080
        :9001            :9002            :9000
             │              │              │
             ▼              ▼              ▼
     ┌─────────────────────────────────────────────┐
     │              Nginx + Certbot                │
     │         (SSL termination, routing)          │
     └─────────────────────────────────────────────┘
                          │
     ┌────────────────────┼────────────────────┐
     ▼                    ▼                    ▼
┌─────────┐         ┌─────────┐         ┌─────────┐
│   Dev   │         │ Staging │         │   Prod  │
│Containers│         │Containers│         │Containers│
└────┬────┘         └────┬────┘         └────┬────┘
     │                   │                   │
     ▼                   ▼                   ▼
 ai_hiring_dev     ai_hiring_staging    ai_hiring_prod
```

## 环境配置

### 触发规则

| 环境 | 触发条件 | 部署方式 |
|------|----------|----------|
| Dev | push to `feature/*` | 自动部署 |
| Staging | push to `master` | 自动部署 |
| Production | manual trigger (workflow_dispatch) | 手动触发 |

### 端口分配

| 环境 | 前端 | 后端 | AI服务 | 数据库 |
|------|------|------|--------|--------|
| Dev | 3001 | 8081 | 9001 | ai_hiring_dev |
| Staging | 3002 | 8082 | 9002 | ai_hiring_staging |
| Production | 3000 | 8080 | 9000 | ai_hiring_prod |

### 端口安全策略

**对外开放（AWS Firewall）：**
- `22` — SSH
- `80` — HTTP (Certbot 验证)
- `443` — HTTPS (流量入口)

**内部端口（不对外）：**
- `3000-3002` — 前端容器
- `8080-8082` — 后端容器
- `9000-9002` — AI 服务容器
- `5432` — PostgreSQL
- `6333` — Qdrant
- `9000` (MinIO) — 文件存储

## 技术选型

| 组件 | 选择 | 理由 |
|------|------|------|
| CI 平台 | GitHub Actions | 原生集成，免费额度充足 |
| 反向代理 | Nginx + Certbot | 成熟稳定，社区资源丰富 |
| 容器镜像仓库 | GitHub Container Registry (ghcr.io) | 与 GitHub Actions 无缝集成 |
| 数据库策略 | 同一 PostgreSQL，三个数据库 | 资源节省，管理简单 |
| E2E 测试 | Playwright | 跨浏览器，功能强大 |

## 目录结构

### VPS 目录结构

```
/opt/ai-hiring/
├── docker/
│   ├── dev/
│   │   ├── docker-compose.yml
│   │   └── .env
│   ├── staging/
│   │   ├── docker-compose.yml
│   │   └── .env
│   └── prod/
│       ├── docker-compose.yml
│       └── .env
├── nginx/
│   ├── nginx.conf
│   └── sites/
│       ├── dev.conf
│       ├── staging.conf
│       └── prod.conf
├── certs/                    # Let's Encrypt 证书
└── scripts/
    ├── deploy.sh             # 通用部署脚本
    └── health-check.sh       # 健康检查
```

### 仓库目录结构

```
.github/
└── workflows/
    ├── ci.yml              # 所有分支：构建 + 测试 + Lint
    ├── deploy-dev.yml      # feature/* 推送 → 部署 Dev
    ├── deploy-staging.yml  # master 推送 → 部署 Staging
    └── deploy-prod.yml     # 手动触发 → 部署 Production

docker/
├── dev/
│   └── docker-compose.yml
├── staging/
│   └── docker-compose.yml
└── prod/
    └── docker-compose.yml

e2e-tests/
├── package.json
├── playwright.config.ts
├── tests/
│   ├── auth.spec.ts
│   ├── resume.spec.ts
│   ├── job.spec.ts
│   └── match.spec.ts
└── fixtures/
    ├── sample-resume.pdf
    └── sample-jd.txt

scripts/
├── deploy.sh
└── health-check.sh
```

## GitHub Actions Workflows

### CI Workflow (ci.yml)

```yaml
name: CI

on:
  push:
    branches: ['**']
  pull_request:
    branches: [master]

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: ./gradlew test
      - run: ./gradlew build

  ai-service:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with:
          python-version: '3.12'
      - run: pip install -r ai-matching-service/requirements.txt
      - run: pytest ai-matching-service/tests/ --cov=app

  frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json
      - run: cd frontend && npm ci
      - run: cd frontend && npm run lint
      - run: cd frontend && npm run build
```

### Deploy Dev Workflow (deploy-dev.yml)

```yaml
name: Deploy Dev

on:
  push:
    branches: ['feature/*']

jobs:
  ci:
    uses: ./.github/workflows/ci.yml

  build-and-push:
    needs: ci
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Build and push Docker images
        # ... 构建并推送到 ghcr.io

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to VPS
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            /opt/ai-hiring/scripts/deploy.sh dev

  e2e:
    needs: deploy
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: cd e2e-tests && npm ci
      - run: cd e2e-tests && npx playwright install
      - run: cd e2e-tests && npx playwright test --project=chromium
        env:
          BASE_URL: https://dev.xxx.com
```

### Deploy Staging Workflow (deploy-staging.yml)

与 Dev 类似，触发条件为 `push to master`。

### Deploy Prod Workflow (deploy-prod.yml)

```yaml
name: Deploy Production

on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to deploy (e.g., v1.0.0)'
        required: true

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Production
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            /opt/ai-hiring/scripts/deploy.sh prod
```

## Nginx 配置

### 主配置 (nginx.conf)

```nginx
worker_processes auto;

http {
    include /etc/nginx/sites/*.conf;

    # Gzip
    gzip on;
    gzip_types text/plain application/json application/javascript text/css;

    # 后端服务 upstream
    upstream backend_dev { server 127.0.0.1:8081; }
    upstream backend_staging { server 127.0.0.1:8082; }
    upstream backend_prod { server 127.0.0.1:8080; }

    # AI 服务 upstream
    upstream ai_dev { server 127.0.0.1:9001; }
    upstream ai_staging { server 127.0.0.1:9002; }
    upstream ai_prod { server 127.0.0.1:9000; }
}
```

### 站点配置示例 (sites/prod.conf)

```nginx
# HTTP 重定向
server {
    listen 80;
    server_name xxx.com www.xxx.com;
    return 301 https://$server_name$request_uri;
}

# HTTPS
server {
    listen 443 ssl http2;
    server_name xxx.com www.xxx.com;

    ssl_certificate /etc/letsencrypt/live/xxx.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/xxx.com/privkey.pem;

    # SSL 配置
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;

    # 前端
    location / {
        proxy_pass http://127.0.0.1:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # 后端 API
    location /api/ {
        proxy_pass http://backend_prod/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # AI 匹配服务
    location /api/match/ {
        proxy_pass http://ai_prod/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 部署脚本

### deploy.sh

```bash
#!/bin/bash
set -e

ENV=$1

if [ -z "$ENV" ]; then
    echo "Usage: $0 <dev|staging|prod>"
    exit 1
fi

cd /opt/ai-hiring/docker/$ENV

echo "🚀 Deploying $ENV environment..."

# 拉取最新镜像
docker compose pull

# 滚动更新
docker compose up -d --no-deps --build

# 等待启动
echo "⏳ Waiting for services to start..."
sleep 10

# 健康检查
/opt/ai-hiring/scripts/health-check.sh $ENV

echo "✅ $ENV deployment complete!"
```

### health-check.sh

```bash
#!/bin/bash
set -e

ENV=$1

case $ENV in
    dev)
        BACKEND_PORT=8081
        AI_PORT=9001
        ;;
    staging)
        BACKEND_PORT=8082
        AI_PORT=9002
        ;;
    prod)
        BACKEND_PORT=8080
        AI_PORT=9000
        ;;
    *)
        echo "Unknown environment: $ENV"
        exit 1
        ;;
esac

echo "🔍 Health check for $ENV..."

# 检查后端
curl -f http://localhost:$BACKEND_PORT/actuator/health || {
    echo "❌ Backend health check failed"
    exit 1
}

# 检查 AI 服务
curl -f http://localhost:$AI_PORT/health || {
    echo "❌ AI service health check failed"
    exit 1
}

echo "✅ All services healthy"
```

## E2E 测试

### Playwright 配置 (playwright.config.ts)

```typescript
import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: process.env.BASE_URL || 'https://dev.xxx.com',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { browserName: 'chromium' },
    },
  ],
});
```

### 测试用例

| 测试文件 | 测试内容 | 使用 Fixtures |
|----------|----------|---------------|
| auth.spec.ts | 登录流程、JWT 验证 | 测试账号 |
| resume.spec.ts | 简历上传、解析验证 | sample-resume.pdf |
| job.spec.ts | JD 创建、状态管理 | sample-jd.txt |
| match.spec.ts | AI 匹配触发、结果验证 | 已有简历 + JD |

### Fixtures

- `sample-resume.pdf` — 真实简历样例（脱敏）
- `sample-jd.txt` — 真实 JD 样例

## GitHub Secrets

| Secret 名称 | 用途 |
|-------------|------|
| `VPS_HOST` | VPS IP 地址 |
| `VPS_USER` | SSH 用户名 |
| `VPS_SSH_KEY` | SSH 私钥（部署专用） |
| `OPENAI_API_KEY` | OpenAI API 密钥 |

## 测试策略

### Unit Tests
- 部署脚本：使用 `bats` (Bash 测试框架)
- 覆盖：参数验证、错误处理

### Integration Tests
- Docker Compose 配置：`docker compose config`
- Nginx 配置：`nginx -t`
- GitHub Actions 语法：`actionlint`

### E2E Tests
- 部署到 Dev 后自动运行 Playwright
- 使用真实数据验证主流程

### Code Review
- 功能完成后提交 PR
- 人工 Review 通过后合并

## 实施计划

### Phase 1: 基础设施

| 步骤 | 任务 | 验证命令 |
|------|------|----------|
| 1.1 | 创建 VPS 目录结构 | `ls /opt/ai-hiring/` |
| 1.2 | 安装 Docker, Docker Compose, Nginx | `docker --version && nginx -v` |
| 1.3 | 创建 PostgreSQL 数据库 | `psql -l | grep ai_hiring` |
| 1.4 | 配置 Nginx + Certbot SSL | `curl -I https://dev.xxx.com` |

### Phase 2: Docker 配置

| 步骤 | 任务 | 验证命令 |
|------|------|----------|
| 2.1 | 创建各环境 Dockerfile | `docker build -t test .` |
| 2.2 | 创建各环境 docker-compose.yml | `docker compose config` |
| 2.3 | 本地测试容器启动 | `docker compose up -d` |
| 2.4 | 创建部署脚本 | `./scripts/deploy.sh dev` |

### Phase 3: GitHub Actions

| 步骤 | 任务 | 验证命令 |
|------|------|----------|
| 3.1 | CI Workflow (test + build) | `actionlint .github/workflows/ci.yml` |
| 3.2 | Deploy Dev Workflow | `actionlint .github/workflows/deploy-dev.yml` |
| 3.3 | Deploy Staging Workflow | `actionlint .github/workflows/deploy-staging.yml` |
| 3.4 | Deploy Prod Workflow | `actionlint .github/workflows/deploy-prod.yml` |

### Phase 4: E2E 测试

| 步骤 | 任务 | 验证命令 |
|------|------|----------|
| 4.1 | Playwright 项目初始化 | `npx playwright test --list` |
| 4.2 | 编写测试用例 + fixtures | `npx playwright test` |
| 4.3 | 集成到 CI Workflow | 查看 GitHub Actions 日志 |

### Phase 5: Code Review

| 步骤 | 任务 | 验证 |
|------|------|------|
| 5.1 | 提交 PR，人工 Review | PR Approved |
| 5.2 | 合并到 master | merge commit |

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| 单点故障（单 VPS） | 定期备份，监控告警 |
| 数据库隔离不足 | 使用独立数据库，权限分离 |
| SSL 证书过期 | Certbot 自动续期 |
| 部署失败回滚 | Docker 镜像版本标签，保留上一版本 |

## 非目标

- Kubernetes 编排（超出单 VPS 范围）
- 多区域部署
- 自动扩缩容
- 监控告警系统（后续单独实现）
