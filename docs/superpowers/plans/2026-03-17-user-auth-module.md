# User & Auth Module Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement user authentication, authorization, department hierarchy, and role-based access control (RBAC) for the AI Hiring Platform.

**Architecture:** Spring Boot 3.3 monolith with feature-based package layout, JWT authentication with refresh tokens, department-scoped data access, PostgreSQL with Flyway migrations.

**Tech Stack:** Java 21, Spring Boot 3.3, Gradle (Kotlin DSL), Spring Security, Spring Data JPA, Flyway, PostgreSQL, Testcontainers, SpringDoc OpenAPI, JUnit 5, Mockito.

---

## File Structure

```
ai-hiring-backend/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/aihiring/
│   ├── AiHiringApplication.java
│   ├── common/
│   │   ├── config/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── JwtConfig.java
│   │   │   └── OpenApiConfig.java
│   │   ├── entity/
│   │   │   └── BaseEntity.java
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   ├── ResourceNotFoundException.java
│   │   │   └── BusinessException.java
│   │   ├── security/
│   │   │   ├── JwtTokenProvider.java
│   │   │   ├── JwtAuthFilter.java
│   │   │   └── UserDetailsImpl.java
│   │   └── dto/
│   │       └── ApiResponse.java
│   ├── auth/
│   │   ├── AuthController.java
│   │   ├── AuthService.java
│   │   ├── RefreshToken.java
│   │   ├── RefreshTokenRepository.java
│   │   └── dto/
│   │       ├── LoginRequest.java
│   │       ├── TokenResponse.java
│   │       ├── RefreshRequest.java
│   │       └── LogoutRequest.java
│   ├── user/
│   │   ├── UserController.java
│   │   ├── UserService.java
│   │   ├── UserRepository.java
│   │   ├── User.java
│   │   └── dto/
│   │       ├── CreateUserRequest.java
│   │       ├── UpdateUserRequest.java
│   │       └── UserResponse.java
│   ├── department/
│   │   ├── DepartmentController.java
│   │   ├── DepartmentService.java
│   │   ├── DepartmentRepository.java
│   │   ├── Department.java
│   │   └── dto/
│   │       ├── CreateDepartmentRequest.java
│   │       ├── UpdateDepartmentRequest.java
│   │       └── DepartmentResponse.java
│   └── role/
│       ├── RoleController.java
│       ├── RoleService.java
│       ├── RoleRepository.java
│       ├── Role.java
│       ├── Permission.java
│       └── dto/
│           ├── CreateRoleRequest.java
│           ├── UpdateRoleRequest.java
│           └── RoleResponse.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-test.yml
│   └── db/migration/
│       └── V1__initial_schema.sql
└── src/test/java/com/aihiring/
    └── (parallel structure)
```

---

## Chunk 1: Project Setup & Base Infrastructure

### Task 1: Initialize Gradle Project

**Files:**
- Create: `ai-hiring-backend/build.gradle.kts`
- Create: `ai-hiring-backend/settings.gradle.kts`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/AiHiringApplication.java`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
rootProject.name = "ai-hiring-backend"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
```

- [ ] **Step 2: Create build.gradle.kts**

```kotlin
plugins {
    id("java")
    id("org.springframework.boot") version "3.3.0"
    id("org.jetbrains.kotlin.plugin.spring") version "1.9.23" apply false
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

allprojects {
    group = "com.aihiring"
    version = "1.0.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter-web")
        implementation("org.springframework.boot:spring-boot-starter-data-jpa")
        implementation("org.springframework.boot:spring-boot-starter-security")
        implementation("org.springframework.boot:spring-boot-starter-validation")
        implementation("org.flywaydb:flyway-core")
        implementation("org.flywaydb:flyway-database-postgresql")
        implementation("org.postgresql:postgresql")
        implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
        implementation("io.jsonwebtoken:jjwt-api:0.12.5")
        implementation("io.jsonwebtoken:jjwt-impl:0.12.5")
        implementation("io.jsonwebtoken:jjwt-jackson:0.12.5")
        compileOnly("org.projectlombok:lombok")
        annotationProcessor("org.projectlombok:lombok")

        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testImplementation("org.springframework.security:spring-security-test")
        testImplementation("org.testcontainers:junit-jupiter")
        testImplementation("org.testcontainers:postgresql")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 3: Create AiHiringApplication.java**

```java
package com.aihiring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AiHiringApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiHiringApplication.class, args);
    }
}
```

- [ ] **Step 4: Create application.yml**

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/aihiring}
    username: ${DB_USERNAME:aihiring}
    password: ${DB_PASSWORD:aihiring}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true

jwt:
  secret: ${JWT_SECRET:default-secret-key-must-be-at-least-32-chars!}
  access-token-expiration: 7200000
  refresh-token-expiration: 604800000

admin:
  default-password: ${ADMIN_DEFAULT_PASSWORD:admin123}

springdoc:
  api-docs:
    enabled: true
  swagger-ui:
    enabled: true
```

- [ ] **Step 5: Verify project compiles**

Run: `cd ai-hiring-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add ai-hiring-backend/
git commit -m "feat: initialize Spring Boot project structure"
```

---

### Task 2: Create Base Entities & Common Components

**Files:**
- Create: `src/main/java/com/aihiring/common/entity/BaseEntity.java`
- Create: `src/main/java/com/aihiring/common/dto/ApiResponse.java`
- Create: `src/main/java/com/aihiring/common/exception/ResourceNotFoundException.java`
- Create: `src/main/java/com/aihiring/common/exception/BusinessException.java`
- Create: `src/main/java/com/aihiring/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/aihiring/common/dto/ApiResponseTest.java
package com.aihiring.common.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {
    @Test
    void success_shouldReturnCorrectFormat() {
        ApiResponse<String> response = ApiResponse.success("testData");
        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("testData", response.getData());
    }

    @Test
    void error_shouldReturnCorrectFormat() {
        ApiResponse<Void> response = ApiResponse.error(401, "Unauthorized");
        assertEquals(401, response.getCode());
        assertEquals("Unauthorized", response.getMessage());
        assertNull(response.getData());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests ApiResponseTest`
Expected: FAIL - class not found

- [ ] **Step 3: Write ApiResponse.java**

```java
package com.aihiring.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests ApiResponseTest`
Expected: PASS

- [ ] **Step 5: Write BaseEntity.java**

```java
package com.aihiring.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 6: Write exception classes**

```java
// ResourceNotFoundException.java
package com.aihiring.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

// BusinessException.java
package com.aihiring.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
```

- [ ] **Step 7: Write GlobalExceptionHandler.java**

```java
package com.aihiring.common.exception;

import com.aihiring.common.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ex.getMessage()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, "Invalid credentials"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, "Insufficient permissions"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Validation failed"));
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add base entities and common components"
```

---

## Chunk 2: Security & Authentication

### Task 3: JWT Security Infrastructure

**Files:**
- Create: `src/main/java/com/aihiring/common/security/JwtTokenProvider.java`
- Create: `src/main/java/com/aihiring/common/security/UserDetailsImpl.java`
- Create: `src/main/java/com/aihiring/common/security/JwtAuthFilter.java`
- Create: `src/main/java/com/aihiring/common/config/SecurityConfig.java`
- Create: `src/main/java/com/aihiring/common/config/JwtConfig.java`

- [ ] **Step 1: Write the failing test for JwtTokenProvider**

```java
// src/test/java/com/aihiring/common/security/JwtTokenProviderTest.java
package com.aihiring.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    private JwtTokenProvider tokenProvider;
    private static final String SECRET = "test-secret-key-must-be-at-least-32-characters-long!";

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET);
    }

    @Test
    void generateAccessToken_shouldCreateValidToken() {
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        String token = tokenProvider.generateAccessToken(userId, username, java.util.Collections.emptyList());

        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void validateToken_shouldReturnTrueForValidToken() {
        UUID userId = UUID.randomUUID();
        String username = "testuser";
        String token = tokenProvider.generateAccessToken(userId, username, java.util.Collections.emptyList());

        assertTrue(tokenProvider.validateToken(token));
    }

    @Test
    void validateToken_shouldReturnFalseForExpiredToken() {
        String expiredToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(tokenProvider.validateToken(expiredToken));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests JwtTokenProviderTest`
Expected: FAIL - class not found

- [ ] **Step 3: Write JwtTokenProvider.java**

```java
package com.aihiring.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(UUID userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests JwtTokenProviderTest`
Expected: PASS

- [ ] **Step 5: Write UserDetailsImpl.java**

```java
package com.aihiring.common.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {
    private UUID id;
    private String username;
    private String password;
    private boolean enabled;
    private UUID departmentId;
    private List<String> roles;
    private List<String> permissions;

    public static UserDetailsImpl create(UUID id, String username, String password,
            boolean enabled, UUID departmentId, List<String> roles, List<String> permissions) {
        return new UserDetailsImpl(id, username, password, enabled, departmentId, roles, permissions);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return enabled; }
}
```

- [ ] **Step 6: Write JwtAuthFilter.java**

```java
package com.aihiring.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = getTokenFromRequest(request);

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            UUID userId = tokenProvider.getUserIdFromToken(token);
            UserDetailsImpl userDetails = new UserDetailsImpl(
                    userId, "user", null, true, null, Collections.emptyList(), Collections.emptyList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

- [ ] **Step 7: Write SecurityConfig.java**

```java
package com.aihiring.common.config;

import com.aihiring.common.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager() throws Exception {
        return null; // Will be overridden by Spring Security auto-config
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add JWT authentication infrastructure"
```

---

## Chunk 3: Data Models & Migrations

### Task 4: Create Database Migration

**Files:**
- Create: `src/main/resources/db/migration/V1__initial_schema.sql`

- [ ] **Step 1: Write Flyway migration**

```sql
-- V1__initial_schema.sql

-- Permissions
CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Roles
CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Role-Permission junction
CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Departments
CREATE TABLE departments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    parent_id UUID REFERENCES departments(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    department_id UUID REFERENCES departments(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User-Role junction
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Refresh Tokens
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_department ON users(department_id);
CREATE INDEX idx_departments_parent ON departments(parent_id);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
```

- [ ] **Step 2: Commit**

```bash
git add -A
git commit -m "feat: add database migration for initial schema"
```

---

### Task 5: Create JPA Entities

**Files:**
- Create: `src/main/java/com/aihiring/role/Permission.java`
- Create: `src/main/java/com/aihiring/role/Role.java`
- Create: `src/main/java/com/aihiring/department/Department.java`
- Create: `src/main/java/com/aihiring/user/User.java`
- Create: `src/main/java/com/aihiring/auth/RefreshToken.java`

- [ ] **Step 1: Write failing test for Role entity**

```java
// src/test/java/com/aihiring/role/RoleTest.java
package com.aihiring.role;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RoleTest {
    @Test
    void role_shouldHaveCorrectFields() {
        Role role = new Role();
        role.setName("TEST_ROLE");
        role.setDescription("Test role");

        assertEquals("TEST_ROLE", role.getName());
        assertEquals("Test role", role.getDescription());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests RoleTest`
Expected: FAIL - class not found

- [ ] **Step 3: Write Permission.java**

```java
package com.aihiring.role;

import com.aihiring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "permissions")
@Getter
@Setter
public class Permission extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;
}
```

- [ ] **Step 4: Write Role.java**

```java
package com.aihiring.role;

import com.aihiring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
@Getter
@Setter
public class Role extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new HashSet<>();
}
```

- [ ] **Step 5: Write Department.java**

```java
package com.aihiring.department;

import com.aihiring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "departments")
@Getter
@Setter
public class Department extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    @OneToMany(mappedBy = "parent", fetch = FetchType.LAZY)
    private List<Department> children = new ArrayList<>();
}
```

- [ ] **Step 6: Write User.java**

```java
package com.aihiring.user;

import com.aihiring.common.entity.BaseEntity;
import com.aihiring.department.Department;
import com.aihiring.role.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User extends BaseEntity {
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new HashSet<>();
}
```

- [ ] **Step 7: Write RefreshToken.java**

```java
package com.aihiring.auth;

import com.aihiring.common.entity.BaseEntity;
import com.aihiring.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken extends BaseEntity {
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew test --tests RoleTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: add JPA entities for Role, Permission, Department, User, RefreshToken"
```

---

## Chunk 4: Repositories

### Task 6: Create Repositories

**Files:**
- Create: `src/main/java/com/aihiring/user/UserRepository.java`
- Create: `src/main/java/com/aihiring/department/DepartmentRepository.java`
- Create: `src/main/java/com/aihiring/role/RoleRepository.java`
- Create: `src/main/java/com/aihiring/role/PermissionRepository.java`
- Create: `src/main/java/com/aihiring/auth/RefreshTokenRepository.java`

- [ ] **Step 1: Write failing test**

```java
// src/test/java/com/aihiring/user/UserRepositoryTest.java
package com.aihiring.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_shouldReturnUser() {
        // This tests the repository method exists
        var result = userRepository.findByUsername("test");
        assertNull(result); // No data seeded
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests UserRepositoryTest`
Expected: FAIL - no bean found

- [ ] **Step 3: Write UserRepository.java**

```java
package com.aihiring.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
```

- [ ] **Step 4: Write DepartmentRepository.java**

```java
package com.aihiring.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByParentIsNull();

    @Query("SELECT d FROM Department d WHERE d.parent.id = :parentId")
    List<Department> findByParentId(UUID parentId);

    boolean existsByNameAndParentId(String name, UUID parentId);
}
```

- [ ] **Step 5: Write RoleRepository.java**

```java
package com.aihiring.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByName(String name);
    boolean existsByName(String name);
}
```

- [ ] **Step 6: Write PermissionRepository.java**

```java
package com.aihiring.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    Optional<Permission> findByName(String name);
    boolean existsByName(String name);
}
```

- [ ] **Step 7: Write RefreshTokenRepository.java**

```java
package com.aihiring.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user.id = :userId")
    void revokeAllByUserId(UUID userId);
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `./gradlew test --tests UserRepositoryTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat: add repositories for User, Department, Role, Permission, RefreshToken"
```

---

## Chunk 5: Auth Module Implementation

### Task 7: Auth Service & Controller

**Files:**
- Modify: `src/main/java/com/aihiring/auth/AuthService.java` (create)
- Create: `src/main/java/com/aihiring/auth/AuthController.java`
- Create: `src/main/java/com/aihiring/auth/dto/LoginRequest.java`
- Create: `src/main/java/com/aihiring/auth/dto/TokenResponse.java`
- Create: `src/main/java/com/aihiring/auth/dto/RefreshRequest.java`
- Create: `src/main/java/com/aihiring/auth/dto/LogoutRequest.java`

- [ ] **Step 1: Write DTOs**

```java
// LoginRequest.java
package com.aihiring.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}

// TokenResponse.java
package com.aihiring.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
}

// RefreshRequest.java
package com.aihiring.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {
    @NotBlank
    private String refreshToken;
}

// LogoutRequest.java
package com.aihiring.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LogoutRequest {
    @NotBlank
    private String refreshToken;
}
```

- [ ] **Step 2: Write failing test for AuthService**

```java
// src/test/java/com/aihiring/auth/AuthServiceTest.java
package com.aihiring.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_withValidCredentials_shouldReturnTokens() {
        // Arrange
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        User user = new User();
        user.setId(java.util.UUID.randomUUID());
        user.setUsername("testuser");
        user.setPassword("encodedPassword");
        user.setEnabled(true);

        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("password", "encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("accessToken");
        when(jwtTokenProvider.generateRefreshToken(any())).thenReturn("refreshToken");

        // Act
        TokenResponse response = authService.login(request);

        // Assert
        assertNotNull(response);
        assertEquals("accessToken", response.getAccessToken());
    }

    @Test
    void login_withInvalidPassword_shouldThrowException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrongPassword");

        User user = new User();
        user.setUsername("testuser");
        user.setPassword("encodedPassword");

        when(userRepository.findByUsername("testuser")).thenReturn(java.util.Optional.of(user));
        when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests AuthServiceTest`
Expected: FAIL - class not found

- [ ] **Step 4: Write AuthService.java**

```java
package com.aihiring.auth;

import com.aihiring.auth.dto.*;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.security.JwtTokenProvider;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new BusinessException(403, "Account is disabled");
        }

        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // Save refresh token
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hashToken(refreshToken));
        token.setUser(user);
        token.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(token);

        return new TokenResponse(accessToken, refreshToken, 7200);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(request.getRefreshToken()))
                .orElseThrow(() -> new BusinessException(401, "Invalid refresh token"));

        if (token.isRevoked() || token.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new BusinessException(401, "Refresh token expired or revoked");
        }

        User user = token.getUser();
        List<String> roles = user.getRoles().stream()
                .map(r -> r.getName())
                .collect(Collectors.toList());

        // Revoke old token
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        // Generate new tokens
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        RefreshToken newToken = new RefreshToken();
        newToken.setTokenHash(hashToken(refreshToken));
        newToken.setUser(user);
        newToken.setExpiresAt(java.time.LocalDateTime.now().plusDays(7));
        refreshTokenRepository.save(newToken);

        return new TokenResponse(accessToken, refreshToken, 7200);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(hashToken(request.getRefreshToken()))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}
```

- [ ] **Step 5: Write AuthController.java**

```java
package com.aihiring.auth;

import com.aihiring.auth.dto.*;
import com.aihiring.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests AuthServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add authentication service and controller"
```

---

## Chunk 6: User Module Implementation

### Task 8: User Service & Controller

**Files:**
- Create: `src/main/java/com/aihiring/user/UserService.java`
- Create: `src/main/java/com/aihiring/user/UserController.java`
- Create: `src/main/java/com/aihiring/user/dto/CreateUserRequest.java`
- Create: `src/main/java/com/aihiring/user/dto/UpdateUserRequest.java`
- Create: `src/main/java/com/aihiring/user/dto/UserResponse.java`

- [ ] **Step 1: Write DTOs**

```java
// CreateUserRequest.java
package com.aihiring.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateUserRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank
    @Size(min = 6, max = 100)
    private String password;

    @NotBlank
    @Email
    private String email;

    private UUID departmentId;
    private List<UUID> roleIds;
}

// UpdateUserRequest.java
package com.aihiring.user.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateUserRequest {
    @Email
    private String email;
    private Boolean enabled;
    private UUID departmentId;
    private List<UUID> roleIds;
}

// UserResponse.java
package com.aihiring.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private boolean enabled;
    private DepartmentDto department;
    private List<RoleDto> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class DepartmentDto {
        private UUID id;
        private String name;
    }

    @Data
    public static class RoleDto {
        private UUID id;
        private String name;
    }
}
```

- [ ] **Step 2: Write failing test for UserService**

```java
// src/test/java/com/aihiring/user/UserServiceTest.java
package com.aihiring.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.aihiring.user.dto.CreateUserRequest;
import com.aihiring.common.exception.BusinessException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void createUser_withValidRequest_shouldCreateUser() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setEmail("new@test.com");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        User result = userService.createUser(request);

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_withDuplicateUsername_shouldThrowException() {
        CreateUserRequest request = new CreateUserRequest();
        request.setUsername("existinguser");
        request.setPassword("password123");
        request.setEmail("new@test.com");

        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThrows(BusinessException.class, () -> userService.createUser(request));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests UserServiceTest`
Expected: FAIL - class not found

- [ ] **Step 4: Write UserService.java**

```java
package com.aihiring.user;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.department.Department;
import com.aihiring.department.DepartmentRepository;
import com.aihiring.role.Role;
import com.aihiring.role.RoleRepository;
import com.aihiring.user.dto.CreateUserRequest;
import com.aihiring.user.dto.UpdateUserRequest;
import com.aihiring.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(409, "Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException(409, "Email already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setEnabled(true);

        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            user.setDepartment(dept);
        }

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            List<Role> roles = roleRepository.findAllById(request.getRoleIds());
            user.getRoles().addAll(roles);
        }

        return userRepository.save(user);
    }

    public Page<User> getUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public User getUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public User updateUser(UUID id, UpdateUserRequest request) {
        User user = getUserById(id);

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmail(request.getEmail())) {
                throw new BusinessException(409, "Email already exists");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }

        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            user.setDepartment(dept);
        }

        if (request.getRoleIds() != null) {
            List<Role> roles = roleRepository.findAllById(request.getRoleIds());
            user.getRoles().clear();
            user.getRoles().addAll(roles);
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(UUID id) {
        User user = getUserById(id);
        userRepository.delete(user);
    }
}
```

- [ ] **Step 5: Write UserController.java**

```java
package com.aihiring.user;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.user.dto.CreateUserRequest;
import com.aihiring.user.dto.UpdateUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userService.createUser(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<Page<User>> getUsers(Pageable pageable) {
        return ApiResponse.success(userService.getUsers(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public ApiResponse<User> getUser(@PathVariable UUID id) {
        return ApiResponse.success(userService.getUserById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<User> updateUser(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success(userService.updateUser(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<Void> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew test --tests UserServiceTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add user service and controller"
```

---

## Chunk 7: Department Module Implementation

### Task 9: Department Service & Controller

**Files:**
- Create: `src/main/java/com/aihiring/department/DepartmentService.java`
- Create: `src/main/java/com/aihiring/department/DepartmentController.java`
- Create: `src/main/java/com/aihiring/department/dto/CreateDepartmentRequest.java`
- Create: `src/main/java/com/aihiring/department/dto/UpdateDepartmentRequest.java`
- Create: `src/main/java/com/aihiring/department/dto/DepartmentResponse.java`

- [ ] **Step 1: Write DTOs**

```java
// CreateDepartmentRequest.java
package com.aihiring.department.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateDepartmentRequest {
    @NotBlank
    private String name;
    private UUID parentId;
}

// UpdateDepartmentRequest.java
package com.aihiring.department.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateDepartmentRequest {
    private String name;
    private UUID parentId;
}

// DepartmentResponse.java
package com.aihiring.department.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class DepartmentResponse {
    private UUID id;
    private String name;
    private UUID parentId;
    private List<DepartmentResponse> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: Write failing test for DepartmentService**

```java
// src/test/java/com/aihiring/department/DepartmentServiceTest.java
package com.aihiring.department;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.aihiring.department.dto.CreateDepartmentRequest;
import com.aihiring.common.exception.BusinessException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void createDepartment_withValidRequest_shouldCreate() {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Engineering");

        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        Department result = departmentService.createDepartment(request);

        assertNotNull(result);
        assertEquals("Engineering", result.getName());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests DepartmentServiceTest`
Expected: FAIL - class not found

- [ ] **Step 4: Write DepartmentService.java**

```java
package com.aihiring.department;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.department.dto.CreateDepartmentRequest;
import com.aihiring.department.dto.UpdateDepartmentRequest;
import com.aihiring.department.dto.DepartmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional
    public Department createDepartment(CreateDepartmentRequest request) {
        Department department = new Department();
        department.setName(request.getName());

        if (request.getParentId() != null) {
            Department parent = departmentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent department not found"));
            department.setParent(parent);
        }

        return departmentRepository.save(department);
    }

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Department getDepartmentById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }

    @Transactional
    public Department updateDepartment(UUID id, UpdateDepartmentRequest request) {
        Department department = getDepartmentById(id);

        if (request.getName() != null) {
            department.setName(request.getName());
        }

        if (request.getParentId() != null) {
            // Cycle detection
            if (request.getParentId().equals(id)) {
                throw new BusinessException(400, "Department cannot be its own parent");
            }

            // Check for circular reference
            UUID newParentId = request.getParentId();
            Department current = department;
            while (current.getParent() != null) {
                if (current.getParent().getId().equals(newParentId)) {
                    throw new BusinessException(400, "Cannot create circular department hierarchy");
                }
                current = current.getParent();
            }

            Department parent = departmentRepository.findById(newParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent department not found"));
            department.setParent(parent);
        }

        return departmentRepository.save(department);
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        Department department = getDepartmentById(id);

        // Check for children
        if (!department.getChildren().isEmpty()) {
            throw new BusinessException(409, "Cannot delete department with children");
        }

        // Check for users
        if (department.getUsers() != null && !department.getUsers().isEmpty()) {
            throw new BusinessException(409, "Cannot delete department with users");
        }

        departmentRepository.delete(department);
    }

    public List<DepartmentResponse> getDepartmentTree() {
        List<Department> roots = departmentRepository.findByParentIsNull();
        return roots.stream()
                .map(this::toTreeResponse)
                .collect(Collectors.toList());
    }

    private DepartmentResponse toTreeResponse(Department department) {
        DepartmentResponse response = new DepartmentResponse();
        response.setId(department.getId());
        response.setName(department.getName());
        response.setParentId(department.getParent() != null ? department.getParent().getId() : null);
        response.setCreatedAt(department.getCreatedAt());
        response.setUpdatedAt(department.getUpdatedAt());

        if (department.getChildren() != null && !department.getChildren().isEmpty()) {
            response.setChildren(department.getChildren().stream()
                    .map(this::toTreeResponse)
                    .collect(Collectors.toList()));
        }

        return response;
    }
}
```

- [ ] **Step 5: Write DepartmentController.java**

```java
package com.aihiring.department;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.department.dto.CreateDepartmentRequest;
import com.aihiring.department.dto.UpdateDepartmentRequest;
import com.aihiring.department.dto.DepartmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('department:manage')")
    public ApiResponse<Department> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.success(departmentService.createDepartment(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<List<DepartmentResponse>> getDepartments() {
        return ApiResponse.success(departmentService.getDepartmentTree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<Department> getDepartment(@PathVariable UUID id) {
        return ApiResponse.success(departmentService.getDepartmentById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public ApiResponse<Department> updateDepartment(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.success(departmentService.updateDepartment(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public ApiResponse<Void> deleteDepartment(@PathVariable UUID id) {
        departmentService.deleteDepartment(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 6: Fix Department entity - add users collection**

```java
// Add to Department.java
@OneToMany(mappedBy = "department", fetch = FetchType.LAZY)
private Set<User> users = new HashSet<>();
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests DepartmentServiceTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add department service and controller"
```

---

## Chunk 8: Role Module Implementation

### Task 10: Role Service & Controller

**Files:**
- Create: `src/main/java/com/aihiring/role/RoleService.java`
- Create: `src/main/java/com/aihiring/role/RoleController.java`
- Create: `src/main/java/com/aihiring/role/dto/CreateRoleRequest.java`
- Create: `src/main/java/com/aihiring/role/dto/UpdateRoleRequest.java`
- Create: `src/main/java/com/aihiring/role/dto/RoleResponse.java`

- [ ] **Step 1: Write DTOs**

```java
// CreateRoleRequest.java
package com.aihiring.role.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateRoleRequest {
    @NotBlank
    private String name;
    private String description;
    private List<UUID> permissionIds;
}

// UpdateRoleRequest.java
package com.aihiring.role.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateRoleRequest {
    private String name;
    private String description;
    private List<UUID> permissionIds;
}

// RoleResponse.java
package com.aihiring.role.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class RoleResponse {
    private UUID id;
    private String name;
    private String description;
    private List<PermissionDto> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class PermissionDto {
        private UUID id;
        private String name;
    }
}
```

- [ ] **Step 2: Write failing test for RoleService**

```java
// src/test/java/com/aihiring/role/RoleServiceTest.java
package com.aihiring.role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.aihiring.role.dto.CreateRoleRequest;
import com.aihiring.common.exception.BusinessException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void createRole_withValidRequest_shouldCreate() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("HR_ADMIN");
        request.setDescription("HR Administrator");

        when(roleRepository.existsByName("HR_ADMIN")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> {
            Role r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Role result = roleService.createRole(request);

        assertNotNull(result);
        assertEquals("HR_ADMIN", result.getName());
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew test --tests RoleServiceTest`
Expected: FAIL - class not found

- [ ] **Step 4: Write RoleService.java**

```java
package com.aihiring.role;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.role.dto.CreateRoleRequest;
import com.aihiring.role.dto.UpdateRoleRequest;
import com.aihiring.role.dto.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public Role createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new BusinessException(409, "Role already exists");
        }

        Role role = new Role();
        role.setName(request.getName());
        role.setDescription(request.getDescription());

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
            role.getPermissions().addAll(permissions);
        }

        return roleRepository.save(role);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    @Transactional
    public Role updateRole(UUID id, UpdateRoleRequest request) {
        Role role = getRoleById(id);

        if (request.getName() != null && !request.getName().equals(role.getName())) {
            if (roleRepository.existsByName(request.getName())) {
                throw new BusinessException(409, "Role name already exists");
            }
            role.setName(request.getName());
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getPermissionIds() != null) {
            List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
        }

        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = getRoleById(id);
        // Check if role is assigned to users
        if (!role.getUsers().isEmpty()) {
            throw new BusinessException(409, "Cannot delete role assigned to users");
        }
        roleRepository.delete(role);
    }
}
```

- [ ] **Step 5: Add users collection to Role entity**

```java
// Add to Role.java
@ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
private Set<User> users = new HashSet<>();
```

- [ ] **Step 6: Write RoleController.java**

```java
package com.aihiring.role;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.role.dto.CreateRoleRequest;
import com.aihiring.role.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<Role> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.success(roleService.createRole(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    public ApiResponse<List<Role>> getRoles() {
        return ApiResponse.success(roleService.getAllRoles());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<Role> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.success(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run: `./gradlew test --tests RoleServiceTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add role service and controller"
```

---

## Chunk 9: Seed Data & Integration Tests

### Task 11: Add Seed Data Migration & Integration Tests

**Files:**
- Create: `src/main/resources/db/migration/V2__seed_data.sql`
- Create: `src/test/resources/application-test.yml`
- Create: `src/test/java/com/aihiring/auth/AuthControllerIntegrationTest.java`

- [ ] **Step 1: Write seed data migration**

```sql
-- V2__seed_data.sql

-- Permissions
INSERT INTO permissions (id, name, description) VALUES
    ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'user:read', 'Read users'),
    ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'user:manage', 'Create/update/delete users'),
    ('c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13', 'department:read', 'Read departments'),
    ('d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14', 'department:manage', 'Create/update/delete departments'),
    ('e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15', 'role:read', 'Read roles'),
    ('f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16', 'role:manage', 'Create/update/delete roles'),
    ('01000000-0000-0000-0000-000000000001', 'resume:read', 'Read resumes'),
    ('01000000-0000-0000-0000-000000000002', 'resume:manage', 'Manage resumes'),
    ('01000000-0000-0000-0000-000000000003', 'job:read', 'Read job descriptions'),
    ('01000000-0000-0000-0000-000000000004', 'job:manage', 'Manage job descriptions'),
    ('01000000-0000-0000-0000-000000000005', 'match:read', 'Read match results'),
    ('01000000-0000-0000-0000-000000000006', 'match:execute', 'Execute matching');

-- Roles
INSERT INTO roles (id, name, description) VALUES
    ('02000000-0000-0000-0000-000000000001', 'SUPER_ADMIN', 'Super Administrator'),
    ('02000000-0000-0000-0000-000000000002', 'HR_ADMIN', 'HR Administrator'),
    ('02000000-0000-0000-0000-000000000003', 'DEPT_ADMIN', 'Department Administrator'),
    ('02000000-0000-0000-0000-000000000004', 'USER', 'Regular User');

-- Role-Permission assignments
-- SUPER_ADMIN gets all permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '02000000-0000-0000-0000-000000000001', id FROM permissions;

-- HR_ADMIN
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('02000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
    ('02000000-0000-0000-0000-000000000002', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12'),
    ('02000000-0000-0000-0000-000000000002', 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13'),
    ('02000000-0000-0000-0000-000000000002', 'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14'),
    ('02000000-0000-0000-0000-000000000002', 'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15'),
    ('02000000-0000-0000-0000-000000000002', 'f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16'),
    ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000001'),
    ('02000000-0000-0000-0000-000000000002', '01000000-0000-0000-0000-000000000002'),
    ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000003'),
    ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000004'),
    ('02000000-0000-0000-0000-000000000005', '01000000-0000-0000-0000-000000000005'),
    ('02000000-0000-0000-0000-000000000006', '01000000-0000-0000-0000-000000000006');

-- DEPT_ADMIN
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('02000000-0000-0000-0000-000000000003', 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11'),
    ('02000000-0000-0000-0000-000000000003', 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13'),
    ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000001'),
    ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000002'),
    ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000003'),
    ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000004'),
    ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000005'),
    ('02000000-0000-0000-0000-000000000003', '01000000-0000-0000-0000-000000000006');

-- USER
INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000001'),
    ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000003'),
    ('02000000-0000-0000-0000-000000000004', '01000000-0000-0000-0000-000000000005');

-- Default Department
INSERT INTO departments (id, name, parent_id) VALUES
    ('03000000-0000-0000-0000-000000000001', 'Headquarters', NULL),
    ('03000000-0000-0000-0000-000000000002', 'Engineering', '03000000-0000-0000-0000-000000000001'),
    ('03000000-0000-0000-0000-000000000003', 'Human Resources', '03000000-0000-0000-0000-000000000001');

-- Default Admin User (password: admin123)
INSERT INTO users (id, username, password, email, enabled, department_id) VALUES
    ('04000000-0000-0000-0000-000000000001', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.rsW4WzOFbMB3dHI.Hu', 'admin@aihiring.com', TRUE, '03000000-0000-0000-0000-000000000001');

-- Assign admin to SUPER_ADMIN role
INSERT INTO user_roles (user_id, role_id) VALUES
    ('04000000-0000-0000-0000-000000000001', '02000000-0000-0000-0000-000000000001');
```

- [ ] **Step 2: Write test configuration**

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/aihiring_test
    username: aihiring
    password: aihiring
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true

jwt:
  secret: test-secret-key-must-be-at-least-32-characters-long!
  access-token-expiration: 7200000
  refresh-token-expiration: 604800000

admin:
  default-password: admin123
```

- [ ] **Step 3: Write integration test for AuthController**

```java
// src/test/java/com/aihiring/auth/AuthControllerIntegrationTest.java
package com.aihiring.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_withValidCredentials_shouldReturn200() throws Exception {
        String requestBody = """
            {
                "username": "admin",
                "password": "admin123"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists());
    }

    @Test
    void login_withInvalidCredentials_shouldReturn401() throws Exception {
        String requestBody = """
            {
                "username": "admin",
                "password": "wrongpassword"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
```

- [ ] **Step 4: Run integration tests**

Run: `./gradlew test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add seed data and integration tests"
```

---

## Plan Complete

**Plan complete and saved to `docs/superpowers/plans/2026-03-17-user-auth-module.md`. Ready to execute?**

This plan covers the User & Auth module implementation with ~50 bite-sized TDD tasks across 9 chunks:
1. Project Setup & Base Infrastructure
2. Security & Authentication
3. Data Models & Migrations
4. Repositories
5. Auth Module
6. User Module
7. Department Module
8. Role Module
9. Seed Data & Integration Tests

**Next modules to plan:**
- Resume Module
- JD (Job) Module
- AI Matching Module
- Boss Integration Module
