# User & Auth Module Design

**Project**: AI Hiring Platform
**Module**: User & Auth
**Date**: 2026-03-16

---

## 1. Overview

First module of the AI Hiring Platform. Provides user management, department hierarchy, role-based access control (RBAC), and JWT authentication. All other platform modules depend on this foundation.

**Tech stack**: Java 21, Spring Boot 3.3, Gradle (Kotlin DSL), Spring Security, Spring Data JPA, Flyway, PostgreSQL, Testcontainers, SpringDoc OpenAPI.

---

## 2. Architecture

Monolith-first approach. Single Spring Boot application with feature-based package layout.

```
ai-hiring-backend/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/aihiring/
│   ├── AiHiringApplication.java
│   ├── common/
│   │   ├── config/          # SecurityConfig, JwtConfig, OpenApiConfig
│   │   ├── entity/          # BaseEntity (id, createdAt, updatedAt)
│   │   ├── exception/       # GlobalExceptionHandler, custom exceptions
│   │   ├── security/        # JwtTokenProvider, JwtAuthFilter, UserDetailsImpl
│   │   └── dto/             # ApiResponse wrapper
│   ├── auth/
│   │   ├── AuthController.java
│   │   ├── AuthService.java
│   │   ├── RefreshToken.java        # Entity
│   │   ├── RefreshTokenRepository.java
│   │   └── dto/             # LoginRequest, TokenResponse, RefreshRequest, LogoutRequest
│   ├── user/
│   │   ├── UserController.java
│   │   ├── UserService.java
│   │   ├── UserRepository.java
│   │   ├── User.java
│   │   └── dto/             # CreateUserRequest, UserResponse
│   ├── department/
│   │   ├── DepartmentController.java
│   │   ├── DepartmentService.java
│   │   ├── DepartmentRepository.java
│   │   ├── Department.java
│   │   └── dto/
│   └── role/
│       ├── RoleController.java
│       ├── RoleService.java
│       ├── RoleRepository.java
│       ├── Role.java
│       ├── Permission.java
│       └── dto/
├── src/main/resources/
│   ├── application.yml
│   ├── application-test.yml
│   └── db/migration/        # Flyway migrations
└── src/test/java/com/aihiring/
```

---

## 3. Data Model

### Entities

**User**

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| username | String | Unique, not null |
| password | String | BCrypt hashed, not null |
| email | String | Unique, not null |
| enabled | Boolean | Default true |
| department_id | UUID | FK → Department, nullable |
| created_at | Timestamp | Auto-set |
| updated_at | Timestamp | Auto-set |

**RefreshToken**

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| token_hash | String | SHA-256 hash of token, unique, not null |
| user_id | UUID | FK → User, not null |
| expires_at | Timestamp | Not null |
| revoked | Boolean | Default false |
| created_at | Timestamp | Auto-set |

**Department**

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | Not null |
| parent_id | UUID | FK → Department, nullable (root) |
| created_at | Timestamp | Auto-set |
| updated_at | Timestamp | Auto-set |

**Role**

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | Unique, not null |
| description | String | Nullable |
| created_at | Timestamp | Auto-set |
| updated_at | Timestamp | Auto-set |

**Permission**

| Field | Type | Constraints |
|-------|------|-------------|
| id | UUID | PK, auto-generated |
| name | String | Unique, not null (e.g. `resume:read`) |
| description | String | Nullable |

### Relationships

```
User (many-to-one)  → Department
User (many-to-many) → Role         (via user_roles join table)
Role (many-to-many) → Permission   (via role_permissions join table)
Department (self-ref) → parent Department
```

### Join Tables

**user_roles**: user_id (FK), role_id (FK), composite PK
**role_permissions**: role_id (FK), permission_id (FK), composite PK

---

## 4. Authentication & Security

### JWT Flow

1. `POST /api/auth/login` with username + password
2. Server validates credentials, returns `{ accessToken, refreshToken }`
3. Client sends `Authorization: Bearer <accessToken>` on requests
4. `JwtAuthFilter` validates token, sets Spring SecurityContext
5. On access token expiry, client calls `POST /api/auth/refresh`

### Token Design

| Token | TTL | Storage | Contents |
|-------|-----|---------|----------|
| Access | 2 hours | Client only | userId, username, roles |
| Refresh | 7 days | Database (allows revocation) | userId |

- Signed with HMAC-SHA256 (verifier must pin to `alg=HS256` and reject `alg=none` or asymmetric variants)
- Secret configured via `JWT_SECRET` environment variable (minimum 32 characters of high-entropy random data; application must fail-fast on startup if shorter)
- Refresh tokens stored as SHA-256 hashes in the `refresh_tokens` table (raw token never persisted)
- On refresh: lookup by hash, check `revoked == false` and `expires_at > now`

### Spring Security Config

- **Public**: `/api/auth/login`, `/api/auth/refresh`
- **Swagger** (`/swagger-ui/**`, `/v3/api-docs/**`): public in `dev` and `test` profiles only; disabled in `prod` profile via `springdoc.api-docs.enabled=false`
- **All other endpoints**: require valid JWT
- **Method-level**: `@PreAuthorize` with role/permission checks
- **Password**: BCrypt encoding

### Error Responses

- 400: validation errors (malformed request body)
- 401: invalid/expired token, bad credentials
- 403: insufficient permissions
- 409: conflict (duplicate username/email, or deleting department/role still in use)

---

## 5. API Endpoints

**Note**: There is no self-registration endpoint. User creation is admin-only via `POST /api/users`.

### Auth (`/api/auth`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | No | Login, returns tokens |
| POST | `/api/auth/refresh` | No | Refresh access token |
| POST | `/api/auth/logout` | Authenticated | Revoke refresh token (idempotent: returns 200 even if token is unknown/already revoked) |

### Users (`/api/users`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/users` | `user:manage` | Create user |
| GET | `/api/users` | `user:read` | List users (paginated, see Pagination below) |
| GET | `/api/users/me` | Authenticated | Current user profile |
| GET | `/api/users/{id}` | `user:read` | Get user detail (`{id}` is UUID type) |
| PUT | `/api/users/{id}` | `user:manage` | Update user |
| DELETE | `/api/users/{id}` | `user:manage` | Delete user |

### Departments (`/api/departments`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/departments` | `department:manage` | Create department |
| GET | `/api/departments` | `department:read` | List departments (tree) |
| GET | `/api/departments/{id}` | `department:read` | Get department detail |
| PUT | `/api/departments/{id}` | `department:manage` | Update department |
| DELETE | `/api/departments/{id}` | `department:manage` | Delete department (blocked if has children or users) |

### Roles (`/api/roles`)

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/roles` | `role:manage` | Create role with permissions |
| GET | `/api/roles` | `role:read` | List all roles (unpaginated; expected to remain small) |
| PUT | `/api/roles/{id}` | `role:manage` | Update role / permissions |
| DELETE | `/api/roles/{id}` | `role:manage` | Delete role (blocked if assigned to users) |

### Response Format

All responses wrapped in:
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

Error responses:
```json
{
  "code": 401,
  "message": "Invalid credentials",
  "data": null
}
```

### Pagination

Paginated endpoints use Spring `Pageable` with query parameters:
- `page` (0-based, default: 0)
- `size` (default: 20, max: 100)
- `sort` (e.g., `sort=createdAt,desc`)

Paginated response wraps data in:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [ ... ],
    "totalElements": 150,
    "totalPages": 8,
    "number": 0,
    "size": 20
  }
}
```

### DTO Schemas

**LoginRequest**
```json
{ "username": "string (required)", "password": "string (required)" }
```

**TokenResponse**
```json
{ "accessToken": "string", "refreshToken": "string", "expiresIn": 7200 }
```

**LogoutRequest**
```json
{ "refreshToken": "string (required)" }
```

**RefreshRequest**
```json
{ "refreshToken": "string (required)" }
```

**CreateUserRequest**
```json
{ "username": "string (required)", "password": "string (required)", "email": "string (required)", "departmentId": "UUID (optional)", "roleIds": ["UUID"] (max 10, duplicates ignored) }
```

**UpdateUserRequest**
```json
{ "email": "string (optional)", "enabled": "boolean (optional)", "departmentId": "UUID (optional)", "roleIds": ["UUID"] (optional, max 10, duplicates ignored) }
```

**UserResponse**
```json
{ "id": "UUID", "username": "string", "email": "string", "enabled": true, "department": { "id": "UUID", "name": "string" }, "roles": [{ "id": "UUID", "name": "string" }], "createdAt": "timestamp", "updatedAt": "timestamp" }
```

**CreateDepartmentRequest**
```json
{ "name": "string (required)", "parentId": "UUID (optional)" }
```

**UpdateDepartmentRequest**
```json
{ "name": "string (optional)", "parentId": "UUID (optional, cycle detection required — reject if new parentId would create a circular hierarchy)" }
```

**DepartmentResponse**
```json
{ "id": "UUID", "name": "string", "parentId": "UUID or null", "children": [ DepartmentResponse ], "createdAt": "timestamp", "updatedAt": "timestamp" }
```

**CreateRoleRequest**
```json
{ "name": "string (required)", "description": "string (optional)", "permissionIds": ["UUID"] (max 50, duplicates ignored) }
```

**UpdateRoleRequest**
```json
{ "name": "string (optional)", "description": "string (optional)", "permissionIds": ["UUID"] (optional, max 50, duplicates ignored — replaces all permissions when provided) }
```

**RoleResponse**
```json
{ "id": "UUID", "name": "string", "description": "string", "permissions": [{ "id": "UUID", "name": "string" }], "createdAt": "timestamp", "updatedAt": "timestamp" }
```

---

## 6. Seed Data

Created via Flyway migration on first startup.

### Default Roles & Permissions

| Role | Permissions |
|------|-------------|
| SUPER_ADMIN | All permissions (`*`) |
| HR_ADMIN | `user:*`, `department:*`, `role:read`, `role:manage`, `resume:*`, `job:*`, `match:*` |
| DEPT_ADMIN | `user:read`, `department:read`, `resume:*`, `job:*`, `match:*` (department-scoped) |
| USER | `resume:read`, `job:read`, `match:read` |

### Default Admin

- Username: `admin`, Role: SUPER_ADMIN
- Password from environment variable `ADMIN_DEFAULT_PASSWORD` (no hardcoded default)

### Department Scoping

DEPT_ADMIN and USER only see data within their own department. Enforced at the service layer — queries filter by `department_id` from the authenticated user's context.

Scoping rules:
- **DEPT_ADMIN**: sees users in their own department and all child departments in the hierarchy
- **USER**: sees users in their own department only (no child departments)
- **Users with no department** (`department_id` is null): can only see themselves via `/api/users/me`; list endpoints return empty results. This is a transitional state — admin should assign a department.
- **SUPER_ADMIN and HR_ADMIN**: no department scoping, full visibility

This scoping contract applies to this module's user listing and will be reused by future modules (resume, JD) querying the authenticated user's department context from `SecurityContext`.

### Registration

There is no self-registration endpoint. User creation is admin-only via `POST /api/users` (requires `user:manage` permission).

> **Design Decision**: Parent design doc criterion 1 uses "registration" (注册) loosely. This module intentionally replaces self-registration with admin-provisioned accounts for security. See Section 5 note.

---

## 7. Configuration

### application.yml

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/aihiring}
    username: ${DB_USERNAME:aihiring}
    password: ${DB_PASSWORD:aihiring}
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    open-in-view: false
  flyway:
    enabled: true

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: 7200000   # 2 hours
  refresh-token-expiration: 604800000 # 7 days

admin:
  default-password: ${ADMIN_DEFAULT_PASSWORD}
```

### Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| DB_URL | Yes (prod) | PostgreSQL connection URL |
| DB_USERNAME | Yes (prod) | Database username |
| DB_PASSWORD | Yes (prod) | Database password |
| JWT_SECRET | Yes | HMAC-SHA256 signing key (min 32 chars) |
| ADMIN_DEFAULT_PASSWORD | Yes | Initial admin password |

---

## 8. Testing Strategy

**TDD**: Red-green-refactor for all features.

| Layer | Scope | Tools |
|-------|-------|-------|
| Unit | Services, JwtTokenProvider, password encoding | JUnit 5 + Mockito |
| Integration | Repositories, controllers, auth filter chain | Spring Boot Test + Testcontainers (PostgreSQL) |

### Key Test Scenarios

**Auth**: login success/failure, token generation/validation, token refresh, expired token handling
**User**: CRUD operations, duplicate username/email, permission enforcement
**Department**: CRUD, tree structure, delete blocked when has children/users, cycle detection on re-parenting
**Role**: CRUD, permission assignment, role-user association
**Security**: unauthorized access, forbidden access, department scoping

---

## 9. Git Workflow

```
master (protected, deployment source)
  └── feature/user-auth (development via worktree)
        → TDD development
        → All tests pass
        → PR created
        → User approval
        → Merge to master
```

- Development happens on feature branches in git worktrees
- PRs require approval before merging to master
- All deployments come from master branch
