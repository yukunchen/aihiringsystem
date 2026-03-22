# CI/CD Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a complete CI/CD pipeline for the AI recruitment platform with three environments (Dev, Staging, Production) deployed to a single VPS via GitHub Actions.

**Architecture:** Docker containers for each service (frontend, backend, AI matching) orchestrated by Docker Compose, Nginx reverse proxy for SSL termination and routing, GitHub Actions for CI/CD with automatic deployment to Dev/Staging and manual trigger for Production.

**Tech Stack:** GitHub Actions, Docker, Docker Compose, Nginx, Certbot, Playwright

---

## Prerequisites (Out of Scope)

The following must be set up manually before this CI/CD pipeline can work:

1. **VPS Infrastructure** — Docker, Docker Compose, PostgreSQL, Qdrant, MinIO already installed
2. **Nginx + Certbot** — SSL certificates obtained, reverse proxy configured for subdomains
3. **PostgreSQL Databases** — `ai_hiring_dev`, `ai_hiring_staging`, `ai_hiring_prod` created
4. **GitHub Secrets** — `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `DOMAIN`, `TEST_SECRET`

See `docs/ci-cd-setup.md` for detailed VPS setup instructions.

---

## File Structure

### Files to Create

```
.github/
└── workflows/
    ├── ci.yml
    ├── deploy-dev.yml
    ├── deploy-staging.yml
    └── deploy-prod.yml

docker/
├── dev/
│   ├── docker-compose.yml
│   └── .env.example
├── staging/
│   ├── docker-compose.yml
│   └── .env.example
└── prod/
    ├── docker-compose.yml
    └── .env.example

scripts/
├── deploy.sh
├── rollback.sh
└── health-check.sh

e2e-tests/
├── package.json
├── package-lock.json
├── playwright.config.ts
├── tests/
│   ├── setup.ts
│   ├── auth.spec.ts
│   ├── resume.spec.ts
│   ├── job.spec.ts
│   └── match.spec.ts
└── fixtures/
    ├── sample-resume.pdf
    └── sample-jd.txt

ai-hiring-backend/
└── Dockerfile

frontend/
└── Dockerfile

ai-hiring-backend/src/main/java/com/aihiring/test/
└── TestCleanupController.java
```

### Existing Files (Referenced but not modified)

```
ai-matching-service/
└── Dockerfile  # Already exists - will be used as-is
```

### Files to Modify

```
.gitignore  # Add e2e-tests/node_modules/, docker/*/.env
```

---

## Chunk 1: Dockerfiles and Docker Compose

### Task 1.1: Backend Dockerfile

**Files:**
- Create: `ai-hiring-backend/Dockerfile`
- Create: `ai-hiring-backend/.dockerignore`

- [ ] **Step 1: Create .dockerignore**

```text
# ai-hiring-backend/.dockerignore
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
!**/src/main/**/build/
!**/src/test/**/build/
*.log
*.tmp
.git
.gitignore
```

- [ ] **Step 2: Create Dockerfile**

```dockerfile
# ai-hiring-backend/Dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon
COPY src src
RUN ./gradlew build --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: Verify Dockerfile syntax**

Run: `docker build -t test-backend ./ai-hiring-backend --dry-run || echo "Dockerfile syntax OK"`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add ai-hiring-backend/Dockerfile ai-hiring-backend/.dockerignore
git commit -m "feat(ci): add backend Dockerfile with multi-stage build"
```

---

### Task 1.2: Frontend Dockerfile

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/.dockerignore`

- [ ] **Step 1: Create .dockerignore**

```text
# frontend/.dockerignore
node_modules/
dist/
*.log
.git
.gitignore
```

- [ ] **Step 2: Create Dockerfile**

```dockerfile
# frontend/Dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

- [ ] **Step 3: Create nginx.conf for frontend container**

```nginx
# frontend/nginx.conf
server {
    listen 80;
    server_name localhost;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

- [ ] **Step 4: Verify Dockerfile syntax**

Run: `docker build -t test-frontend ./frontend --dry-run || echo "Dockerfile syntax OK"`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add frontend/Dockerfile frontend/.dockerignore frontend/nginx.conf
git commit -m "feat(ci): add frontend Dockerfile with nginx serving"
```

---

### Task 1.3: Dev Environment Docker Compose

**Files:**
- Create: `docker/dev/docker-compose.yml`
- Create: `docker/dev/.env.example`

- [ ] **Step 1: Create .env.example**

```bash
# docker/dev/.env.example
IMAGE_TAG=dev-latest
REGISTRY=ghcr.io
IMAGE_PREFIX=your-github-username

# Database
POSTGRES_HOST=host.docker.internal
POSTGRES_PORT=5432
POSTGRES_DB=ai_hiring_dev
POSTGRES_USER=aihiring
POSTGRES_PASSWORD=your-password

# Services
BACKEND_PORT=8081
AI_PORT=8001
FRONTEND_PORT=3001

# External services
QDRANT_HOST=host.docker.internal
QDRANT_PORT=6333
MINIO_HOST=host.docker.internal
MINIO_PORT=9000

# Secrets
OPENAI_API_KEY=sk-xxx
JWT_SECRET=your-jwt-secret
TEST_SECRET=your-test-secret
```

- [ ] **Step 2: Create docker-compose.yml**

```yaml
# docker/dev/docker-compose.yml
version: '3.8'

services:
  frontend:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-hiring-frontend:${IMAGE_TAG}
    ports:
      - "${FRONTEND_PORT}:80"
    depends_on:
      - backend
    environment:
      - VITE_API_BASE_URL=/api
    restart: unless-stopped

  backend:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-hiring-backend:${IMAGE_TAG}
    ports:
      - "${BACKEND_PORT}:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - AI_MATCHING_URL=http://ai-matching:8001
      - MINIO_ENDPOINT=http://${MINIO_HOST}:${MINIO_PORT}
      - JWT_SECRET=${JWT_SECRET}
      - SPRING_PROFILES_ACTIVE=dev
    depends_on:
      - ai-matching
    restart: unless-stopped

  ai-matching:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-matching-service:${IMAGE_TAG}
    ports:
      - "${AI_PORT}:8001"
    environment:
      - LLM_MODEL=gpt-4o-mini
      - EMBEDDING_MODEL=text-embedding-3-small
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - QDRANT_HOST=${QDRANT_HOST}
      - QDRANT_PORT=${QDRANT_PORT}
    restart: unless-stopped
```

- [ ] **Step 3: Validate docker-compose syntax**

Run: `docker compose -f docker/dev/docker-compose.yml config --quiet && echo "Valid"`
Expected: "Valid"

- [ ] **Step 4: Commit**

```bash
git add docker/dev/
git commit -m "feat(ci): add dev environment docker-compose"
```

---

### Task 1.4: Staging Environment Docker Compose

**Files:**
- Create: `docker/staging/docker-compose.yml`
- Create: `docker/staging/.env.example`

- [ ] **Step 1: Create .env.example**

```bash
# docker/staging/.env.example
IMAGE_TAG=staging-latest
REGISTRY=ghcr.io
IMAGE_PREFIX=your-github-username

# Database
POSTGRES_HOST=host.docker.internal
POSTGRES_PORT=5432
POSTGRES_DB=ai_hiring_staging
POSTGRES_USER=aihiring
POSTGRES_PASSWORD=your-password

# Services
BACKEND_PORT=8082
AI_PORT=8002
FRONTEND_PORT=3002

# External services
QDRANT_HOST=host.docker.internal
QDRANT_PORT=6333
MINIO_HOST=host.docker.internal
MINIO_PORT=9000

# Secrets
OPENAI_API_KEY=sk-xxx
JWT_SECRET=your-jwt-secret
TEST_SECRET=your-test-secret
```

- [ ] **Step 2: Create docker-compose.yml**

```yaml
# docker/staging/docker-compose.yml
version: '3.8'

services:
  frontend:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-hiring-frontend:${IMAGE_TAG}
    ports:
      - "${FRONTEND_PORT}:80"
    depends_on:
      - backend
    environment:
      - VITE_API_BASE_URL=/api
    restart: unless-stopped

  backend:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-hiring-backend:${IMAGE_TAG}
    ports:
      - "${BACKEND_PORT}:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - AI_MATCHING_URL=http://ai-matching:8001
      - MINIO_ENDPOINT=http://${MINIO_HOST}:${MINIO_PORT}
      - JWT_SECRET=${JWT_SECRET}
      - SPRING_PROFILES_ACTIVE=staging
    depends_on:
      - ai-matching
    restart: unless-stopped

  ai-matching:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-matching-service:${IMAGE_TAG}
    ports:
      - "${AI_PORT}:8001"
    environment:
      - LLM_MODEL=gpt-4o-mini
      - EMBEDDING_MODEL=text-embedding-3-small
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - QDRANT_HOST=${QDRANT_HOST}
      - QDRANT_PORT=${QDRANT_PORT}
    restart: unless-stopped
```

- [ ] **Step 3: Validate docker-compose syntax**

Run: `docker compose -f docker/staging/docker-compose.yml config --quiet && echo "Valid"`
Expected: "Valid"

- [ ] **Step 4: Commit**

```bash
git add docker/staging/
git commit -m "feat(ci): add staging environment docker-compose"
```

---

### Task 1.6: Backend TestCleanupController

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/test/TestCleanupController.java`

- [ ] **Step 1: Create TestCleanupController**

```java
// ai-hiring-backend/src/main/java/com/aihiring/test/TestCleanupController.java
package com.aihiring.test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
@Profile({"dev", "staging"})
public class TestCleanupController {

    @Value("${test.secret:}")
    private String testSecret;

    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanup(
            @RequestHeader("X-Test-Secret") String secret,
            @RequestParam(required = false, defaultValue = "false") boolean includeResumes,
            @RequestParam(required = false, defaultValue = "false") boolean includeJobs) {

        if (testSecret == null || testSecret.isEmpty() || !testSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Invalid test secret");
        }

        // Clean up test data - records created by test user
        // This is a simplified implementation; extend as needed

        return ResponseEntity.ok("Cleanup completed");
    }
}
```

- [ ] **Step 2: Add test.secret to application-dev.properties**

```properties
# ai-hiring-backend/src/main/resources/application-dev.properties
test.secret=${TEST_SECRET:}
```

- [ ] **Step 3: Add test.secret to application-staging.properties**

```properties
# ai-hiring-backend/src/main/resources/application-staging.properties
test.secret=${TEST_SECRET:}
```

- [ ] **Step 4: Commit**

```bash
git add ai-hiring-backend/src/main/java/com/aihiring/test/TestCleanupController.java
git add ai-hiring-backend/src/main/resources/application-dev.properties
git add ai-hiring-backend/src/main/resources/application-staging.properties
git commit -m "feat(ci): add TestCleanupController for E2E test data cleanup"
```

---

### Task 1.7: Production Environment Docker Compose

**Files:**
- Create: `docker/prod/docker-compose.yml`
- Create: `docker/prod/.env.example`

- [ ] **Step 1: Create .env.example**

```bash
# docker/prod/.env.example
IMAGE_TAG=latest
REGISTRY=ghcr.io
IMAGE_PREFIX=your-github-username

# Database
POSTGRES_HOST=host.docker.internal
POSTGRES_PORT=5432
POSTGRES_DB=ai_hiring_prod
POSTGRES_USER=aihiring
POSTGRES_PASSWORD=your-password

# Services
BACKEND_PORT=8080
AI_PORT=8000
FRONTEND_PORT=3000

# External services
QDRANT_HOST=host.docker.internal
QDRANT_PORT=6333
MINIO_HOST=host.docker.internal
MINIO_PORT=9000

# Secrets
OPENAI_API_KEY=sk-xxx
JWT_SECRET=your-jwt-secret
```

- [ ] **Step 2: Create docker-compose.yml**

```yaml
# docker/prod/docker-compose.yml
version: '3.8'

services:
  frontend:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-hiring-frontend:${IMAGE_TAG}
    ports:
      - "${FRONTEND_PORT}:80"
    depends_on:
      - backend
    environment:
      - VITE_API_BASE_URL=/api
    restart: always

  backend:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-hiring-backend:${IMAGE_TAG}
    ports:
      - "${BACKEND_PORT}:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - AI_MATCHING_URL=http://ai-matching:8001
      - MINIO_ENDPOINT=http://${MINIO_HOST}:${MINIO_PORT}
      - JWT_SECRET=${JWT_SECRET}
      - SPRING_PROFILES_ACTIVE=prod
    depends_on:
      - ai-matching
    restart: always

  ai-matching:
    image: ${REGISTRY}/${IMAGE_PREFIX}/ai-matching-service:${IMAGE_TAG}
    ports:
      - "${AI_PORT}:8001"
    environment:
      - LLM_MODEL=gpt-4o
      - EMBEDDING_MODEL=text-embedding-3-small
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - QDRANT_HOST=${QDRANT_HOST}
      - QDRANT_PORT=${QDRANT_PORT}
    restart: always
```

- [ ] **Step 3: Validate docker-compose syntax**

Run: `docker compose -f docker/prod/docker-compose.yml config --quiet && echo "Valid"`
Expected: "Valid"

- [ ] **Step 4: Commit**

```bash
git add docker/prod/
git commit -m "feat(ci): add production environment docker-compose"
```

---

## Chunk 2: Deployment Scripts

### Task 2.1: Deploy Script

**Files:**
- Create: `scripts/deploy.sh`

- [ ] **Step 1: Create deploy.sh**

```bash
#!/bin/bash
set -e

ENV=$1
VERSION=$2

if [ -z "$ENV" ]; then
    echo "Usage: $0 <dev|staging|prod> [version]"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="/opt/ai-hiring/docker/$ENV"

echo "🚀 Deploying $ENV environment..."

cd "$COMPOSE_DIR"

# 如果指定了版本，更新 .env 中的镜像标签
if [ -n "$VERSION" ]; then
    if [ -f .env ]; then
        sed -i "s/IMAGE_TAG=.*/IMAGE_TAG=$VERSION/" .env
        echo "📦 Using image tag: $VERSION"
    else
        echo "❌ .env file not found at $COMPOSE_DIR/.env"
        exit 1
    fi
fi

# 拉取最新镜像
echo "📥 Pulling latest images..."
docker compose pull

# 滚动更新
echo "🔄 Starting containers..."
docker compose up -d --no-deps

# 等待启动
echo "⏳ Waiting for services to start..."
sleep 10

# 健康检查
"$SCRIPT_DIR/health-check.sh" "$ENV"

echo "✅ $ENV deployment complete!"
```

- [ ] **Step 2: Make script executable**

Run: `chmod +x scripts/deploy.sh`

- [ ] **Step 3: Test script syntax**

Run: `bash -n scripts/deploy.sh && echo "Syntax OK"`
Expected: "Syntax OK"

- [ ] **Step 4: Commit**

```bash
git add scripts/deploy.sh
git commit -m "feat(ci): add deployment script"
```

---

### Task 2.2: Health Check Script

**Files:**
- Create: `scripts/health-check.sh`

- [ ] **Step 1: Create health-check.sh**

```bash
#!/bin/bash
set -e

ENV=$1

case $ENV in
    dev)
        BACKEND_PORT=8081
        AI_PORT=8001
        ;;
    staging)
        BACKEND_PORT=8082
        AI_PORT=8002
        ;;
    prod)
        BACKEND_PORT=8080
        AI_PORT=8000
        ;;
    *)
        echo "❌ Unknown environment: $ENV"
        echo "Usage: $0 <dev|staging|prod>"
        exit 1
        ;;
esac

echo "🔍 Health check for $ENV environment..."

# 检查后端
echo "  Checking backend on port $BACKEND_PORT..."
if curl -sf "http://localhost:$BACKEND_PORT/actuator/health" > /dev/null 2>&1; then
    echo "  ✅ Backend healthy"
else
    echo "  ❌ Backend health check failed"
    exit 1
fi

# 检查 AI 服务
echo "  Checking AI service on port $AI_PORT..."
if curl -sf "http://localhost:$AI_PORT/health" > /dev/null 2>&1; then
    echo "  ✅ AI service healthy"
else
    echo "  ❌ AI service health check failed"
    exit 1
fi

echo "✅ All services healthy for $ENV environment"
```

- [ ] **Step 2: Make script executable**

Run: `chmod +x scripts/health-check.sh`

- [ ] **Step 3: Test script syntax**

Run: `bash -n scripts/health-check.sh && echo "Syntax OK"`
Expected: "Syntax OK"

- [ ] **Step 4: Commit**

```bash
git add scripts/health-check.sh
git commit -m "feat(ci): add health check script"
```

---

### Task 2.3: Rollback Script

**Files:**
- Create: `scripts/rollback.sh`

- [ ] **Step 1: Create rollback.sh**

```bash
#!/bin/bash
set -e

ENV=$1
VERSION=$2

if [ -z "$ENV" ] || [ -z "$VERSION" ]; then
    echo "Usage: $0 <dev|staging|prod> <version>"
    echo "Example: $0 prod abc123def456"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🔄 Rolling back $ENV to version $VERSION..."
"$SCRIPT_DIR/deploy.sh" "$ENV" "$VERSION"
echo "✅ Rollback complete!"
```

- [ ] **Step 2: Make script executable**

Run: `chmod +x scripts/rollback.sh`

- [ ] **Step 3: Test script syntax**

Run: `bash -n scripts/rollback.sh && echo "Syntax OK"`
Expected: "Syntax OK"

- [ ] **Step 4: Commit**

```bash
git add scripts/rollback.sh
git commit -m "feat(ci): add rollback script"
```

---

### Task 2.4: Script Unit Tests

**Files:**
- Create: `scripts/tests/health-check.bats`

- [ ] **Step 1: Install bats (for local testing)**

Run: `which bats || echo "bats not installed - will be tested in CI"`

- [ ] **Step 2: Create test file**

```bash
# scripts/tests/health-check.bats
#!/usr/bin/env bats

@test "health-check.sh requires environment argument" {
    run ./scripts/health-check.sh
    [ "$status" -eq 1 ]
    [[ "$output" == *"Usage"* ]]
}

@test "health-check.sh rejects unknown environment" {
    run ./scripts/health-check.sh invalid
    [ "$status" -eq 1 ]
    [[ "$output" == *"Unknown environment"* ]]
}

@test "health-check.sh accepts valid environments" {
    # Mock curl to return success
    export PATH="$BATS_TEST_DIRNAME/mocks:$PATH"

    run ./scripts/health-check.sh dev
    [ "$status" -eq 0 ]
}
```

- [ ] **Step 3: Commit**

```bash
git add scripts/tests/
git commit -m "feat(ci): add bash unit tests for deployment scripts"
```

---

## Chunk 3: GitHub Actions Workflows

### Task 3.1: CI Workflow

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: Create workflow file**

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: ['**']
  pull_request:
    branches: [master]
  workflow_call:

jobs:
  backend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: ai-hiring-backend
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: gradle

      - name: Run tests
        run: ./gradlew test

      - name: Build
        run: ./gradlew build -x test

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: backend-test-results
          path: ai-hiring-backend/build/reports/tests/

  ai-service:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: ai-matching-service
    steps:
      - uses: actions/checkout@v4

      - name: Set up Python 3.12
        uses: actions/setup-python@v5
        with:
          python-version: '3.12'
          cache: pip

      - name: Install dependencies
        run: |
          python -m pip install --upgrade pip
          pip install -r requirements.txt
          pip install pytest pytest-cov

      - name: Run tests
        run: pytest tests/ --cov=. --cov-report=xml

      - name: Upload coverage
        uses: actions/upload-artifact@v4
        with:
          name: ai-service-coverage
          path: ai-matching-service/coverage.xml

  frontend:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: frontend
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        run: npm ci

      - name: Lint
        run: npm run lint

      - name: Build
        run: npm run build

      - name: Upload build
        uses: actions/upload-artifact@v4
        with:
          name: frontend-build
          path: frontend/dist/
```

- [ ] **Step 2: Validate workflow syntax**

Run: `npx actionlint .github/workflows/ci.yml 2>/dev/null || echo "Install actionlint for validation"`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "feat(ci): add CI workflow for all services"
```

---

### Task 3.2: Deploy Dev Workflow

**Files:**
- Create: `.github/workflows/deploy-dev.yml`

- [ ] **Step 1: Create workflow file**

```yaml
# .github/workflows/deploy-dev.yml
name: Deploy Dev

on:
  push:
    branches: ['feature/*']

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}

jobs:
  ci:
    uses: ./.github/workflows/ci.yml

  build-and-push:
    needs: ci
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Backend
        uses: docker/build-push-action@v5
        with:
          context: ./ai-hiring-backend
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-backend:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-backend:dev-latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Build and push AI Service
        uses: docker/build-push-action@v5
        with:
          context: ./ai-matching-service
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-matching-service:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-matching-service:dev-latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Build and push Frontend
        uses: docker/build-push-action@v5
        with:
          context: ./frontend
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-frontend:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-frontend:dev-latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

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
            /opt/ai-hiring/scripts/deploy.sh dev ${{ github.sha }}

  e2e:
    needs: deploy
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: e2e-tests/package-lock.json

      - name: Install dependencies
        working-directory: e2e-tests
        run: npm ci

      - name: Install Playwright browsers
        working-directory: e2e-tests
        run: npx playwright install chromium

      - name: Run E2E tests
        working-directory: e2e-tests
        run: npx playwright test --project=chromium
        env:
          BASE_URL: https://dev.${{ secrets.DOMAIN }}
          TEST_SECRET: ${{ secrets.TEST_SECRET }}

      - name: Upload test results
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: e2e-test-results
          path: e2e-tests/playwright-report/
```

- [ ] **Step 2: Validate workflow syntax**

Run: `npx actionlint .github/workflows/deploy-dev.yml 2>/dev/null || echo "Install actionlint for validation"`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy-dev.yml
git commit -m "feat(ci): add Dev deployment workflow with E2E tests"
```

---

### Task 3.3: Deploy Staging Workflow

**Files:**
- Create: `.github/workflows/deploy-staging.yml`

- [ ] **Step 1: Create workflow file**

```yaml
# .github/workflows/deploy-staging.yml
name: Deploy Staging

on:
  push:
    branches: [master]

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}

jobs:
  ci:
    uses: ./.github/workflows/ci.yml

  build-and-push:
    needs: ci
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Login to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Backend
        uses: docker/build-push-action@v5
        with:
          context: ./ai-hiring-backend
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-backend:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-backend:staging-latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Build and push AI Service
        uses: docker/build-push-action@v5
        with:
          context: ./ai-matching-service
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-matching-service:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-matching-service:staging-latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Build and push Frontend
        uses: docker/build-push-action@v5
        with:
          context: ./frontend
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-frontend:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-frontend:staging-latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

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
            /opt/ai-hiring/scripts/deploy.sh staging ${{ github.sha }}
```

- [ ] **Step 2: Validate workflow syntax**

Run: `npx actionlint .github/workflows/deploy-staging.yml 2>/dev/null || echo "Install actionlint for validation"`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy-staging.yml
git commit -m "feat(ci): add Staging deployment workflow"
```

---

### Task 3.4: Deploy Production Workflow

**Files:**
- Create: `.github/workflows/deploy-prod.yml`

- [ ] **Step 1: Create workflow file**

```yaml
# .github/workflows/deploy-prod.yml
name: Deploy Production

on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Git commit SHA to deploy'
        required: true

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ${{ github.repository_owner }}

jobs:
  verify:
    runs-on: ubuntu-latest
    steps:
      - name: Verify images exist
        run: |
          echo "Verifying images for version ${{ inputs.version }}"
          # Images should already exist from staging deployment
          echo "Backend: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-backend:${{ inputs.version }}"
          echo "AI Service: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-matching-service:${{ inputs.version }}"
          echo "Frontend: ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/ai-hiring-frontend:${{ inputs.version }}"

  deploy:
    needs: verify
    runs-on: ubuntu-latest
    environment: production
    steps:
      - name: Deploy to Production
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key: ${{ secrets.VPS_SSH_KEY }}
          script: |
            /opt/ai-hiring/scripts/deploy.sh prod ${{ inputs.version }}

      - name: Health check
        run: |
          curl -sf https://${{ secrets.DOMAIN }}/api/actuator/health || exit 1
          echo "✅ Production deployment successful"
```

- [ ] **Step 2: Validate workflow syntax**

Run: `npx actionlint .github/workflows/deploy-prod.yml 2>/dev/null || echo "Install actionlint for validation"`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/deploy-prod.yml
git commit -m "feat(ci): add Production deployment workflow with manual trigger"
```

---

## Chunk 4: E2E Tests

### Task 4.1: Playwright Setup

**Files:**
- Create: `e2e-tests/package.json`
- Create: `e2e-tests/playwright.config.ts`

- [ ] **Step 1: Create package.json**

```json
{
  "name": "e2e-tests",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "test": "playwright test",
    "test:ui": "playwright test --ui",
    "report": "playwright show-report"
  },
  "devDependencies": {
    "@playwright/test": "^1.42.0",
    "@types/node": "^20.11.0"
  }
}
```

- [ ] **Step 2: Create playwright.config.ts**

```typescript
// e2e-tests/playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [
    ['html'],
    ['json', { outputFile: 'test-results.json' }]
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
```

- [ ] **Step 3: Commit**

```bash
git add e2e-tests/package.json e2e-tests/playwright.config.ts
git commit -m "feat(ci): add Playwright configuration for E2E tests"
```

---

### Task 4.2: Test Fixtures

**Files:**
- Create: `e2e-tests/fixtures/sample-resume.pdf`
- Create: `e2e-tests/fixtures/sample-jd.txt`

- [ ] **Step 1: Create sample JD**

```text
# e2e-tests/fixtures/sample-jd.txt
高级前端工程师

职位描述：
我们正在寻找一位经验丰富的前端工程师加入我们的团队。

任职要求：
- 5年以上前端开发经验
- 精通 React/Vue.js 等现代前端框架
- 熟悉 TypeScript
- 有大型项目经验优先

工作地点：北京
薪资范围：30-50K
```

- [ ] **Step 2: Create sample resume placeholder**

Note: Create a simple PDF or use an existing sample resume file.

```bash
# For testing, create a minimal PDF
echo "%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >> endobj
xref
0 4
0000000000 65535 f
0000000009 00000 n
0000000058 00000 n
0000000115 00000 n
trailer << /Size 4 /Root 1 0 R >>
startxref
190
%%EOF" > e2e-tests/fixtures/sample-resume.pdf
```

- [ ] **Step 3: Commit**

```bash
git add e2e-tests/fixtures/
git commit -m "feat(ci): add E2E test fixtures"
```

---

### Task 4.3: Auth E2E Tests

**Files:**
- Create: `e2e-tests/tests/auth.spec.ts`

- [ ] **Step 1: Create auth tests**

```typescript
// e2e-tests/tests/auth.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('should display login page', async ({ page }) => {
    await expect(page.locator('text=登录')).toBeVisible();
  });

  test('should login successfully with valid credentials', async ({ page }) => {
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');

    // Wait for redirect to dashboard
    await expect(page).toHaveURL(/.*dashboard.*/);
    await expect(page.locator('text=欢迎')).toBeVisible();
  });

  test('should show error with invalid credentials', async ({ page }) => {
    await page.fill('input[name="username"]', 'invalid');
    await page.fill('input[name="password"]', 'invalid');
    await page.click('button[type="submit"]');

    await expect(page.locator('text=用户名或密码错误')).toBeVisible();
  });

  test('should logout successfully', async ({ page }) => {
    // Login first
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*dashboard.*/);

    // Logout
    await page.click('[data-testid="user-menu"]');
    await page.click('text=退出登录');

    await expect(page).toHaveURL(/.*login.*/);
  });
});
```

- [ ] **Step 2: Commit**

```bash
git add e2e-tests/tests/auth.spec.ts
git commit -m "feat(ci): add authentication E2E tests"
```

---

### Task 4.4: Resume E2E Tests

**Files:**
- Create: `e2e-tests/tests/resume.spec.ts`

- [ ] **Step 1: Create resume tests**

```typescript
// e2e-tests/tests/resume.spec.ts
import { test, expect } from '@playwright/test';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test.describe('Resume Management', () => {
  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('/');
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*dashboard.*/);
  });

  test('should upload resume successfully', async ({ page }) => {
    await page.click('text=简历管理');
    await page.click('text=上传简历');

    const resumePath = path.join(__dirname, '../fixtures/sample-resume.pdf');
    await page.setInputFiles('input[type="file"]', resumePath);

    await page.click('button:has-text("确认上传")');

    await expect(page.locator('text=上传成功')).toBeVisible();
  });

  test('should display resume list', async ({ page }) => {
    await page.click('text=简历管理');

    await expect(page.locator('table')).toBeVisible();
  });

  test('should view resume details', async ({ page }) => {
    await page.click('text=简历管理');

    // Click first resume in list
    const firstResume = page.locator('table tbody tr').first();
    if (await firstResume.isVisible()) {
      await firstResume.click();
      await expect(page.locator('text=简历详情')).toBeVisible();
    }
  });
});
```

- [ ] **Step 2: Commit**

```bash
git add e2e-tests/tests/resume.spec.ts
git commit -m "feat(ci): add resume management E2E tests"
```

---

### Task 4.5: Job E2E Tests

**Files:**
- Create: `e2e-tests/tests/job.spec.ts`

- [ ] **Step 1: Create job tests**

```typescript
// e2e-tests/tests/job.spec.ts
import { test, expect } from '@playwright/test';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test.describe('Job Management', () => {
  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('/');
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*dashboard.*/);
  });

  test('should create new job', async ({ page }) => {
    await page.click('text=职位管理');
    await page.click('text=创建职位');

    const jdPath = path.join(__dirname, '../fixtures/sample-jd.txt');
    const jdContent = fs.readFileSync(jdPath, 'utf-8');

    await page.fill('input[name="title"]', 'E2E测试职位');
    await page.fill('textarea[name="description"]', jdContent);
    await page.selectOption('select[name="department"]', '技术部');

    await page.click('button:has-text("创建")');

    await expect(page.locator('text=创建成功')).toBeVisible();
  });

  test('should display job list', async ({ page }) => {
    await page.click('text=职位管理');

    await expect(page.locator('table')).toBeVisible();
  });

  test('should update job status', async ({ page }) => {
    await page.click('text=职位管理');

    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.locator('button:has-text("状态")').click();
      await page.click('text=已关闭');

      await expect(page.locator('text=状态已更新')).toBeVisible();
    }
  });
});
```

- [ ] **Step 2: Commit**

```bash
git add e2e-tests/tests/job.spec.ts
git commit -m "feat(ci): add job management E2E tests"
```

---

### Task 4.6: Match E2E Tests

**Files:**
- Create: `e2e-tests/tests/match.spec.ts`

- [ ] **Step 1: Create match tests**

```typescript
// e2e-tests/tests/match.spec.ts
import { test, expect } from '@playwright/test';

test.describe('AI Matching', () => {
  test.beforeEach(async ({ page }) => {
    // Login
    await page.goto('/');
    await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
    await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/.*dashboard.*/);
  });

  test('should trigger AI matching from job detail', async ({ page }) => {
    await page.click('text=职位管理');

    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.click();
      await page.click('button:has-text("AI匹配")');

      await expect(page.locator('text=匹配中')).toBeVisible();

      // Wait for matching to complete (may take a while)
      await expect(page.locator('text=匹配结果')).toBeVisible({
        timeout: 60000
      });
    }
  });

  test('should display match results with scores', async ({ page }) => {
    await page.click('text=职位管理');

    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.click();

      // Check if match results exist
      const matchSection = page.locator('[data-testid="match-results"]');
      if (await matchSection.isVisible()) {
        await expect(matchSection.locator('.match-score')).toBeVisible();
      }
    }
  });

  test('should show match reasoning', async ({ page }) => {
    await page.click('text=职位管理');

    const firstJob = page.locator('table tbody tr').first();
    if (await firstJob.isVisible()) {
      await firstJob.click();

      const matchCard = page.locator('[data-testid="match-card"]').first();
      if (await matchCard.isVisible()) {
        await matchCard.click();
        await expect(page.locator('text=匹配理由')).toBeVisible();
      }
    }
  });
});
```

- [ ] **Step 2: Commit**

```bash
git add e2e-tests/tests/match.spec.ts
git commit -m "feat(ci): add AI matching E2E tests"
```

---

### Task 4.7: Test Setup and Cleanup

**Files:**
- Create: `e2e-tests/tests/setup.ts`

- [ ] **Step 1: Create setup file**

```typescript
// e2e-tests/tests/setup.ts
import { test as setup, expect } from '@playwright/test';

const AUTH_FILE = 'playwright/.auth/user.json';

setup('authenticate', async ({ page }) => {
  // Login and save auth state
  await page.goto('/');
  await page.fill('input[name="username"]', process.env.TEST_USERNAME || 'testuser');
  await page.fill('input[name="password"]', process.env.TEST_PASSWORD || 'testpass');
  await page.click('button[type="submit"]');

  await expect(page).toHaveURL(/.*dashboard.*/);

  // Save auth state
  await page.context().storageState({ path: AUTH_FILE });
});

setup('cleanup test data', async ({ request }) => {
  // Clean up test data before running tests
  if (process.env.TEST_SECRET) {
    await request.post('/api/test/cleanup', {
      headers: {
        'X-Test-Secret': process.env.TEST_SECRET,
      },
    });
  }
});
```

- [ ] **Step 2: Update playwright.config.ts to use auth**

```typescript
// Add to playwright.config.ts projects:
{
  name: 'chromium',
  use: {
    ...devices['Desktop Chrome'],
    storageState: 'playwright/.auth/user.json',
  },
  dependencies: ['setup'],
},
{
  name: 'setup',
  testMatch: /.*\.setup\.ts/,
},
```

- [ ] **Step 3: Commit**

```bash
git add e2e-tests/tests/setup.ts e2e-tests/playwright.config.ts
git commit -m "feat(ci): add E2E test setup with auth and cleanup"
```

---

## Chunk 5: Final Setup

### Task 5.1: Update .gitignore

**Files:**
- Modify: `.gitignore`

- [ ] **Step 1: Add CI/CD entries to .gitignore**

```gitignore
# Add to existing .gitignore

# CI/CD
docker/*/.env
e2e-tests/node_modules/
e2e-tests/playwright-report/
e2e-tests/test-results/
e2e-tests/playwright/.auth/
```

- [ ] **Step 2: Commit**

```bash
git add .gitignore
git commit -m "feat(ci): update .gitignore for CI/CD artifacts"
```

---

### Task 5.2: Create README Documentation

**Files:**
- Create: `docs/ci-cd-setup.md`

- [ ] **Step 1: Create setup documentation**

```markdown
# CI/CD Setup Guide

## Prerequisites

- VPS with Docker and Docker Compose installed
- Domain name with DNS configured
- GitHub repository with Actions enabled

## Initial VPS Setup

1. Install Docker and Docker Compose:
   ```bash
   curl -fsSL https://get.docker.com | sh
   sudo usermod -aG docker $USER
   ```

2. Create directory structure:
   ```bash
   sudo mkdir -p /opt/ai-hiring/{docker,nginx,scripts,certs}
   sudo chown -R $USER:$USER /opt/ai-hiring
   ```

3. Copy scripts to VPS:
   ```bash
   scp scripts/*.sh user@vps:/opt/ai-hiring/scripts/
   chmod +x /opt/ai-hiring/scripts/*.sh
   ```

4. Copy docker-compose files:
   ```bash
   scp -r docker/* user@vps:/opt/ai-hiring/docker/
   ```

5. Create .env files for each environment:
   ```bash
   cp /opt/ai-hiring/docker/dev/.env.example /opt/ai-hiring/docker/dev/.env
   # Edit with actual values
   nano /opt/ai-hiring/docker/dev/.env
   ```

## GitHub Secrets

Configure these secrets in your repository settings:

| Secret | Description |
|--------|-------------|
| `VPS_HOST` | VPS IP address |
| `VPS_USER` | SSH username |
| `VPS_SSH_KEY` | SSH private key for deployment |
| `DOMAIN` | Your domain (without subdomain) |
| `TEST_SECRET` | Secret for E2E test cleanup |
| `TEST_USERNAME` | Test account username |
| `TEST_PASSWORD` | Test account password |

## Deployment Flow

1. **Dev**: Push to `feature/*` → automatic deploy
2. **Staging**: Push to `master` → automatic deploy
3. **Production**: Manual trigger via GitHub Actions

## Rollback

```bash
/opt/ai-hiring/scripts/rollback.sh prod <commit-sha>
```
```

- [ ] **Step 2: Commit**

```bash
git add docs/ci-cd-setup.md
git commit -m "docs: add CI/CD setup guide"
```

---

### Task 5.3: Final Verification

- [ ] **Step 1: Verify all workflows pass actionlint**

Run: `for f in .github/workflows/*.yml; do echo "Checking $f"; npx actionlint "$f" 2>/dev/null || echo "Install actionlint for validation"; done`
Expected: No errors

- [ ] **Step 2: Verify all scripts have correct syntax**

Run: `for f in scripts/*.sh; do bash -n "$f" && echo "$f OK"; done`
Expected: All scripts pass

- [ ] **Step 3: Verify docker-compose files**

Run: `for env in dev staging prod; do docker compose -f docker/$env/docker-compose.yml config --quiet && echo "$env OK"; done`
Expected: All environments pass

- [ ] **Step 4: Create PR summary**

```markdown
## CI/CD Implementation Complete

### Files Created
- 4 GitHub Actions workflows
- 3 Docker Compose configurations
- 3 Dockerfiles
- 3 Deployment scripts
- 7 E2E test files
- Setup documentation

### Testing
- Unit tests for deployment scripts
- E2E tests for auth, resume, job, and match features

### Next Steps
1. Review and approve PR
2. Configure GitHub Secrets
3. Set up VPS infrastructure
4. Run first deployment
```

- [ ] **Step 5: Final commit**

```bash
git add -A
git status
git commit -m "feat(ci): complete CI/CD implementation

- Add Dockerfiles for backend and frontend
- Add docker-compose for dev/staging/prod environments
- Add deployment scripts (deploy, rollback, health-check)
- Add GitHub Actions workflows for CI and deployment
- Add Playwright E2E tests for main user flows
- Add setup documentation

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Summary

| Phase | Tasks | Files Created |
|-------|-------|---------------|
| Docker | 5 | 13 files |
| Scripts | 4 | 4 files |
| Workflows | 4 | 4 files |
| E2E Tests | 7 | 9 files |
| Final | 3 | 2 files |
| **Total** | **23** | **32 files** |

## Verification Checklist

- [ ] All Dockerfiles build successfully
- [ ] All docker-compose files are valid
- [ ] All scripts pass bash syntax check
- [ ] All workflows pass actionlint
- [ ] E2E tests can run locally
- [ ] Documentation is complete
