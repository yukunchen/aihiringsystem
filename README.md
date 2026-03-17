# AI Hiring System

AI-powered recruitment platform (SaaS) for unified resume management across job platforms with intelligent JD-to-resume matching.

## Features

- **Unified Resume Management** — Aggregate resumes from Boss Zhipin integration and manual uploads
- **Intelligent JD Matching** — AI-powered job description to resume matching with scoring and reasoning
- **Resume Parsing Pipeline** — Upload → text extraction → AI structuring → vectorization
- **Role-Based Access Control** — Super admin, HR admin, department admin, and user roles
- **Department Management** — Organize recruitment by departments

## Architecture

Two-service architecture:

| Service | Stack | Responsibility |
|---------|-------|---------------|
| Core Business Service | Spring Boot / Java | User/auth, departments, resumes, JD management, permissions, Boss Zhipin integration |
| AI Matching Service | Python / FastAPI | Resume/JD vectorization, vector DB search, LLM-based match scoring and reasoning |

### Tech Stack

- **Frontend**: React + Ant Design
- **Backend**: Spring Boot (Java), FastAPI (Python)
- **Database**: PostgreSQL (structured data), Milvus/Qdrant (vectors)
- **Storage**: MinIO/OSS (file storage)
- **Infra**: Docker, GitHub Actions CI/CD, Kong/Nginx API gateway
- **Auth**: JWT-based (access token + refresh token)

## API Overview

REST APIs under `/api/` prefix:

- **Auth**: `/api/auth/login`, `/api/auth/refresh`
- **Users**: `/api/users`
- **Resumes**: `/api/resumes`, `/api/resumes/upload`, `/api/resumes/{id}`
- **Jobs**: `/api/jobs`, `/api/jobs/{id}`
- **Match**: `/api/match`
- **Boss Integration**: `/api/boss/auth-url`, `/api/boss/callback`, `/api/boss/sync`

## Development

```bash
# Clone the repository
git clone https://github.com/yukunchen/aihiringsystem.git
cd aihiringsystem
```

See `docs/` for detailed design specs and implementation plans.

## License

All rights reserved.
