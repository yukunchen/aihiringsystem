# Resume Module Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement resume file upload, local storage, text extraction (PDF/Word/TXT), full-text search, and async event hooks for future AI service integration.

**Architecture:** Spring Boot 3.3 monolith extension. Adds `resume` package with file storage interface, text extractors, and search abstraction. Follows existing patterns from User & Auth module (BaseEntity, ApiResponse, @PreAuthorize, Mockito unit tests).

**Tech Stack:** Java 21, Spring Boot 3.3, Apache PDFBox, Apache POI, Spring Data JPA, Flyway, PostgreSQL full-text search, H2 (tests), Testcontainers.

**Spec:** `docs/superpowers/specs/2026-03-18-resume-module-design.md`

---

## File Structure

```
ai-hiring-backend/
├── build.gradle.kts                                          # Modify: add PDFBox + POI deps
├── src/main/java/com/aihiring/
│   ├── common/exception/
│   │   └── GlobalExceptionHandler.java                       # Modify: add MaxUploadSizeExceededException handler
│   └── resume/
│       ├── Resume.java                                       # Create: JPA entity
│       ├── ResumeStatus.java                                 # Create: enum
│       ├── ResumeSource.java                                 # Create: enum
│       ├── ResumeRepository.java                             # Create: JPA repository
│       ├── ResumeSearchRepository.java                       # Create: search interface
│       ├── PostgresResumeSearchRepository.java               # Create: @Profile("!test") FTS impl
│       ├── SimpleResumeSearchRepository.java                 # Create: @Profile("test") LIKE impl
│       ├── ResumeService.java                                # Create: business logic
│       ├── ResumeController.java                             # Create: REST controller
│       ├── ResumeUploadedEvent.java                          # Create: Spring ApplicationEvent
│       ├── dto/
│       │   ├── ResumeResponse.java                           # Create
│       │   ├── ResumeListResponse.java                       # Create
│       │   └── UpdateStructuredRequest.java                  # Create
│       ├── storage/
│       │   ├── FileStorageService.java                       # Create: interface
│       │   └── LocalFileStorageService.java                  # Create: local disk impl
│       └── parser/
│           ├── TextExtractor.java                            # Create: interface
│           ├── PdfTextExtractor.java                         # Create
│           ├── DocxTextExtractor.java                        # Create
│           └── TxtTextExtractor.java                         # Create
├── src/main/resources/
│   ├── application.yml                                       # Modify: add storage + multipart config
│   └── db/migration/
│       └── V3__resume_table.sql                              # Create: resume table + FTS
├── src/test/java/com/aihiring/
│   └── resume/
│       ├── storage/
│       │   └── LocalFileStorageServiceTest.java              # Create
│       ├── parser/
│       │   ├── PdfTextExtractorTest.java                     # Create
│       │   ├── DocxTextExtractorTest.java                    # Create
│       │   └── TxtTextExtractorTest.java                     # Create
│       ├── ResumeServiceTest.java                            # Create
│       ├── ResumeControllerIntegrationTest.java              # Create
│       └── ResumeSearchIntegrationTest.java                  # Create: Testcontainers FTS test
└── src/test/resources/
    ├── schema-h2.sql                                         # Modify: add resumes table
    └── fixtures/
        ├── sample-resume.pdf                                 # Create: test fixture
        ├── sample-resume.docx                                # Create: test fixture
        └── sample-resume.txt                                 # Create: test fixture
```

---

## Chunk 1: Dependencies, Configuration & Database Migration

### Task 1: Add Dependencies

**Files:**
- Modify: `ai-hiring-backend/build.gradle.kts`

- [ ] **Step 1: Add PDFBox and POI dependencies to build.gradle.kts**

Add these lines to the `dependencies` block after the existing `jjwt-jackson` line:

```kotlin
    implementation("org.apache.pdfbox:pdfbox:3.0.2")
    implementation("org.apache.poi:poi-ooxml:5.2.5")
```

- [ ] **Step 2: Verify project compiles**

Run: `cd ai-hiring-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add Apache PDFBox and POI dependencies for resume parsing"
```

---

### Task 2: Add Configuration

**Files:**
- Modify: `ai-hiring-backend/src/main/resources/application.yml`

- [ ] **Step 1: Add storage and multipart configuration**

Append to `application.yml`:

```yaml

storage:
  local:
    base-dir: ${STORAGE_BASE_DIR:./uploads}

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

Note: The existing `spring:` block already has `datasource`, `jpa`, and `flyway` keys. The `servlet.multipart` keys must be added under the same root `spring:` key — merge them into the existing `spring:` block rather than creating a duplicate. The `storage:` block is new and goes at root level.

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/application.yml
git commit -m "config: add file storage and multipart upload settings"
```

---

### Task 3: Create Database Migration

**Files:**
- Create: `ai-hiring-backend/src/main/resources/db/migration/V3__resume_table.sql`

- [ ] **Step 1: Create the Flyway migration**

```sql
-- V3__resume_table.sql

CREATE TYPE resume_source AS ENUM ('MANUAL', 'BOSS');
CREATE TYPE resume_status AS ENUM ('UPLOADED', 'TEXT_EXTRACTED', 'AI_PROCESSED');

CREATE TABLE resumes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    raw_text TEXT,
    source resume_source NOT NULL DEFAULT 'MANUAL',
    status resume_status NOT NULL DEFAULT 'UPLOADED',
    uploaded_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    candidate_name VARCHAR(100),
    candidate_phone VARCHAR(50),
    candidate_email VARCHAR(100),
    education JSONB,
    experience JSONB,
    projects JSONB,
    skills JSONB,
    search_vector TSVECTOR,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_resumes_search_vector ON resumes USING GIN (search_vector);

CREATE OR REPLACE FUNCTION resumes_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('simple', COALESCE(NEW.raw_text, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER resumes_search_vector_trigger
    BEFORE INSERT OR UPDATE ON resumes
    FOR EACH ROW EXECUTE FUNCTION resumes_search_vector_update();

CREATE INDEX idx_resumes_source ON resumes(source);
CREATE INDEX idx_resumes_status ON resumes(status);
CREATE INDEX idx_resumes_uploaded_by ON resumes(uploaded_by);
CREATE INDEX idx_resumes_created_at ON resumes(created_at DESC);
```

- [ ] **Step 2: Create H2-compatible schema for tests**

Modify `ai-hiring-backend/src/test/resources/schema-h2.sql`. If this file does not exist, create it. Add the H2-compatible `resumes` table definition. H2 does not support PostgreSQL enums, TSVECTOR, GIN indexes, or triggers, so use VARCHAR and omit those features:

```sql
-- H2-compatible resumes table (no TSVECTOR, no triggers, no PG enums)
CREATE TABLE IF NOT EXISTS resumes (
    id UUID PRIMARY KEY DEFAULT RANDOM_UUID(),
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    raw_text CLOB,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    uploaded_by UUID NOT NULL,
    candidate_name VARCHAR(100),
    candidate_phone VARCHAR(50),
    candidate_email VARCHAR(100),
    education CLOB,
    experience CLOB,
    projects CLOB,
    skills CLOB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

- [ ] **Step 3: Update application-test.yml for H2 schema and test data**

Update `ai-hiring-backend/src/test/resources/application-test.yml`. Change `ddl-auto` to `none` (H2 schema is managed by `schema-h2.sql` instead) and add `sql.init` config to load both schema and test data:

```yaml
  jpa:
    hibernate:
      ddl-auto: none
```

Add under the existing `spring:` key:

```yaml
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-h2.sql
      data-locations: classpath:test-data.sql
```

This ensures: (a) H2 tables are created from `schema-h2.sql` (not Hibernate auto-DDL), (b) test seed data with deterministic UUIDs is loaded from `test-data.sql`, (c) no conflict between Hibernate DDL and our schema file.

**Also:** The `DataInitializer` (CommandLineRunner) will also run during tests and try to insert duplicate data. To prevent this, add a `@Profile("!test")` annotation to `DataInitializer.java`:

Modify `ai-hiring-backend/src/main/java/com/aihiring/DataInitializer.java` — add import and annotation:

```java
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
```

Also update `schema-h2.sql` to include the existing tables from `V1__initial_schema.sql` (permissions, roles, role_permissions, departments, users, user_roles, refresh_tokens) since Hibernate DDL is now disabled. Copy the table definitions from `V1__initial_schema.sql` using H2-compatible syntax (replace `gen_random_uuid()` with `RANDOM_UUID()`) and append the resumes table at the end.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/db/migration/V3__resume_table.sql src/test/resources/schema-h2.sql src/test/resources/application-test.yml
git commit -m "db: add resume table migration and H2 test schema"
```

---

## Chunk 2: Entity, Enums & Repository

### Task 4: Create Enums

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeStatus.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeSource.java`

- [ ] **Step 1: Create ResumeStatus enum**

```java
package com.aihiring.resume;

public enum ResumeStatus {
    UPLOADED,
    TEXT_EXTRACTED,
    AI_PROCESSED
}
```

- [ ] **Step 2: Create ResumeSource enum**

```java
package com.aihiring.resume;

public enum ResumeSource {
    MANUAL,
    BOSS
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aihiring/resume/
git commit -m "feat(resume): add ResumeStatus and ResumeSource enums"
```

---

### Task 5: Create Resume Entity

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/Resume.java`

- [ ] **Step 1: Create Resume entity**

```java
package com.aihiring.resume;

import com.aihiring.common.entity.BaseEntity;
import com.aihiring.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "resumes")
@Getter
@Setter
public class Resume extends BaseEntity {

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResumeSource source = ResumeSource.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResumeStatus status = ResumeStatus.UPLOADED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "candidate_name", length = 100)
    private String candidateName;

    @Column(name = "candidate_phone", length = 50)
    private String candidatePhone;

    @Column(name = "candidate_email", length = 100)
    private String candidateEmail;

    @Column(columnDefinition = "TEXT")
    private String education;

    @Column(columnDefinition = "TEXT")
    private String experience;

    @Column(columnDefinition = "TEXT")
    private String projects;

    @Column(columnDefinition = "TEXT")
    private String skills;
}
```

Note: `education`, `experience`, `projects`, and `skills` are stored as JSONB in PostgreSQL but mapped as `String` (JSON text) in JPA. This avoids needing `hypersistence-utils`. The service layer handles JSON serialization/deserialization.

- [ ] **Step 2: Verify compilation**

Run: `cd ai-hiring-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aihiring/resume/Resume.java
git commit -m "feat(resume): add Resume JPA entity"
```

---

### Task 6: Create Repository and Search Abstraction

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeRepository.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeSearchRepository.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/PostgresResumeSearchRepository.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/SimpleResumeSearchRepository.java`

- [ ] **Step 1: Create ResumeRepository**

```java
package com.aihiring.resume;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    Page<Resume> findBySource(ResumeSource source, Pageable pageable);
    Page<Resume> findByStatus(ResumeStatus status, Pageable pageable);
    Page<Resume> findBySourceAndStatus(ResumeSource source, ResumeStatus status, Pageable pageable);
}
```

- [ ] **Step 2: Create ResumeSearchRepository interface**

```java
package com.aihiring.resume;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ResumeSearchRepository {
    Page<Resume> search(String query, Pageable pageable);
}
```

- [ ] **Step 3: Create PostgresResumeSearchRepository**

```java
package com.aihiring.resume;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("!test")
public class PostgresResumeSearchRepository implements ResumeSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Page<Resume> search(String query, Pageable pageable) {
        String sql = "SELECT * FROM resumes WHERE search_vector @@ plainto_tsquery('simple', :query) ORDER BY created_at DESC";
        Query nativeQuery = entityManager.createNativeQuery(sql, Resume.class);
        nativeQuery.setParameter("query", query);
        nativeQuery.setFirstResult((int) pageable.getOffset());
        nativeQuery.setMaxResults(pageable.getPageSize());
        List<Resume> results = nativeQuery.getResultList();

        String countSql = "SELECT COUNT(*) FROM resumes WHERE search_vector @@ plainto_tsquery('simple', :query)";
        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("query", query);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }
}
```

- [ ] **Step 4: Create SimpleResumeSearchRepository**

```java
package com.aihiring.resume;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("test")
public class SimpleResumeSearchRepository implements ResumeSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Page<Resume> search(String query, Pageable pageable) {
        String jpql = "SELECT r FROM Resume r WHERE LOWER(r.rawText) LIKE LOWER(:query) ORDER BY r.createdAt DESC";
        TypedQuery<Resume> typedQuery = entityManager.createQuery(jpql, Resume.class);
        typedQuery.setParameter("query", "%" + query + "%");
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Resume> results = typedQuery.getResultList();

        String countJpql = "SELECT COUNT(r) FROM Resume r WHERE LOWER(r.rawText) LIKE LOWER(:query)";
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        countQuery.setParameter("query", "%" + query + "%");
        long total = countQuery.getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `cd ai-hiring-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aihiring/resume/
git commit -m "feat(resume): add ResumeRepository and search abstraction with profile-based implementations"
```

---

## Chunk 3: File Storage & Text Extraction

### Task 7: File Storage Service (TDD)

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/storage/FileStorageService.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/storage/LocalFileStorageService.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/resume/storage/LocalFileStorageServiceTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.aihiring.resume.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService(tempDir.toString());
    }

    @Test
    void store_shouldSaveFileAndReturnPath() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "PDF content".getBytes()
        );

        String storedPath = storageService.store(file, "test-uuid.pdf");

        assertTrue(Files.exists(Path.of(storedPath)));
        assertEquals("PDF content", Files.readString(Path.of(storedPath)));
    }

    @Test
    void load_shouldReturnFileResource() throws IOException {
        Path testFile = tempDir.resolve("resumes").resolve("test.pdf");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "test content");

        Resource resource = storageService.load(testFile.toString());

        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
    }

    @Test
    void load_nonExistentFile_shouldReturnNonExistentResource() {
        Resource resource = storageService.load(tempDir.resolve("nonexistent.pdf").toString());

        assertFalse(resource.exists());
    }

    @Test
    void delete_shouldRemoveFile() throws IOException {
        Path testFile = tempDir.resolve("resumes").resolve("delete-me.pdf");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, "to be deleted");

        storageService.delete(testFile.toString());

        assertFalse(Files.exists(testFile));
    }

    @Test
    void delete_nonExistentFile_shouldNotThrow() {
        assertDoesNotThrow(() ->
            storageService.delete(tempDir.resolve("nonexistent.pdf").toString())
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.storage.LocalFileStorageServiceTest" 2>&1 | tail -20`
Expected: FAIL — classes not found

- [ ] **Step 3: Create FileStorageService interface**

```java
package com.aihiring.resume.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {
    /**
     * Store a file and return the absolute path where it was saved.
     * @param file the uploaded file
     * @param storedName the name to store the file as (e.g., "uuid.pdf")
     * @return absolute path to the stored file
     */
    String store(MultipartFile file, String storedName) throws IOException;

    /**
     * Load a file as a Resource by its stored path.
     */
    Resource load(String filePath);

    /**
     * Delete a file by its stored path. No-op if file doesn't exist.
     */
    void delete(String filePath);
}
```

- [ ] **Step 4: Create LocalFileStorageService**

```java
package com.aihiring.resume.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path baseDir;

    public LocalFileStorageService(@Value("${storage.local.base-dir:./uploads}") String baseDir) {
        this.baseDir = Path.of(baseDir).resolve("resumes");
    }

    @Override
    public String store(MultipartFile file, String storedName) throws IOException {
        Files.createDirectories(baseDir);
        Path target = baseDir.resolve(storedName);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toAbsolutePath().toString();
    }

    @Override
    public Resource load(String filePath) {
        return new FileSystemResource(filePath);
    }

    @Override
    public void delete(String filePath) {
        try {
            Files.deleteIfExists(Path.of(filePath));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", filePath, e);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.storage.LocalFileStorageServiceTest" 2>&1 | tail -20`
Expected: All 5 tests PASS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/aihiring/resume/storage/ src/test/java/com/aihiring/resume/storage/
git commit -m "feat(resume): add FileStorageService with local filesystem implementation"
```

---

### Task 8: Text Extractors (TDD)

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/parser/TextExtractor.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/parser/TxtTextExtractor.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/parser/PdfTextExtractor.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/parser/DocxTextExtractor.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/resume/parser/TxtTextExtractorTest.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/resume/parser/PdfTextExtractorTest.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/resume/parser/DocxTextExtractorTest.java`
- Create: `ai-hiring-backend/src/test/resources/fixtures/sample-resume.txt`
- Create: `ai-hiring-backend/src/test/resources/fixtures/sample-resume.pdf`
- Create: `ai-hiring-backend/src/test/resources/fixtures/sample-resume.docx`

- [ ] **Step 1: Create test fixture files**

Create `src/test/resources/fixtures/sample-resume.txt`:
```
John Smith
Software Engineer
Email: john@example.com
Phone: 555-0123

Education:
B.S. Computer Science, MIT, 2020

Experience:
Senior Developer at TechCorp (2020-2024)
- Built microservices using Java and Spring Boot
- Managed PostgreSQL databases

Skills: Java, Spring Boot, PostgreSQL, Docker
```

For `sample-resume.pdf` and `sample-resume.docx`: These must be generated programmatically in a test setup class or created manually. Create a helper test that generates them:

Create `src/test/java/com/aihiring/resume/parser/TestFixtureGenerator.java`:

```java
package com.aihiring.resume.parser;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestFixtureGenerator {

    private static final String SAMPLE_TEXT = "John Smith\nSoftware Engineer\nSkills: Java, Spring Boot";

    public static Path generatePdf(Path dir) throws IOException {
        Path path = dir.resolve("sample-resume.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.setLeading(14.5f);
                cs.newLineAtOffset(50, 700);
                for (String line : SAMPLE_TEXT.split("\n")) {
                    cs.showText(line);
                    cs.newLine();
                }
                cs.endText();
            }
            doc.save(path.toFile());
        }
        return path;
    }

    public static Path generateDocx(Path dir) throws IOException {
        Path path = dir.resolve("sample-resume.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream os = Files.newOutputStream(path)) {
            for (String line : SAMPLE_TEXT.split("\n")) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(line);
            }
            doc.write(os);
        }
        return path;
    }

    public static String getSampleText() {
        return SAMPLE_TEXT;
    }
}
```

- [ ] **Step 2: Write failing tests for TxtTextExtractor**

```java
package com.aihiring.resume.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class TxtTextExtractorTest {

    private final TxtTextExtractor extractor = new TxtTextExtractor();

    @Test
    void extract_shouldReturnTextContent() throws IOException {
        String content = "John Smith\nSoftware Engineer";
        var input = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        String result = extractor.extract(input);

        assertEquals(content, result);
    }

    @Test
    void extract_emptyFile_shouldReturnEmptyString() throws IOException {
        var input = new ByteArrayInputStream(new byte[0]);

        String result = extractor.extract(input);

        assertEquals("", result);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.parser.TxtTextExtractorTest" 2>&1 | tail -20`
Expected: FAIL — class not found

- [ ] **Step 4: Create TextExtractor interface and TxtTextExtractor**

```java
// src/main/java/com/aihiring/resume/parser/TextExtractor.java
package com.aihiring.resume.parser;

import java.io.IOException;
import java.io.InputStream;

public interface TextExtractor {
    String extract(InputStream inputStream) throws IOException;
}
```

```java
// src/main/java/com/aihiring/resume/parser/TxtTextExtractor.java
package com.aihiring.resume.parser;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class TxtTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.parser.TxtTextExtractorTest" 2>&1 | tail -20`
Expected: All 2 tests PASS

- [ ] **Step 6: Write failing tests for PdfTextExtractor**

```java
package com.aihiring.resume.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @TempDir
    Path tempDir;

    @Test
    void extract_shouldReturnTextFromPdf() throws IOException {
        Path pdfPath = TestFixtureGenerator.generatePdf(tempDir);

        String result;
        try (InputStream is = Files.newInputStream(pdfPath)) {
            result = extractor.extract(is);
        }

        assertTrue(result.contains("John Smith"));
        assertTrue(result.contains("Software Engineer"));
        assertTrue(result.contains("Java"));
    }
}
```

- [ ] **Step 7: Run test to verify it fails**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.parser.PdfTextExtractorTest" 2>&1 | tail -20`
Expected: FAIL — PdfTextExtractor not found

- [ ] **Step 8: Create PdfTextExtractor**

```java
package com.aihiring.resume.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream inputStream) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document).trim();
        }
    }
}
```

- [ ] **Step 9: Run test to verify it passes**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.parser.PdfTextExtractorTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 10: Write failing tests for DocxTextExtractor**

```java
package com.aihiring.resume.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocxTextExtractorTest {

    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @TempDir
    Path tempDir;

    @Test
    void extract_shouldReturnTextFromDocx() throws IOException {
        Path docxPath = TestFixtureGenerator.generateDocx(tempDir);

        String result;
        try (InputStream is = Files.newInputStream(docxPath)) {
            result = extractor.extract(is);
        }

        assertTrue(result.contains("John Smith"));
        assertTrue(result.contains("Software Engineer"));
        assertTrue(result.contains("Java"));
    }
}
```

- [ ] **Step 11: Run test to verify it fails**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.parser.DocxTextExtractorTest" 2>&1 | tail -20`
Expected: FAIL — DocxTextExtractor not found

- [ ] **Step 12: Create DocxTextExtractor**

```java
package com.aihiring.resume.parser;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public String extract(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText().trim();
        }
    }
}
```

- [ ] **Step 13: Run test to verify it passes**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.parser.DocxTextExtractorTest" 2>&1 | tail -20`
Expected: PASS

- [ ] **Step 14: Run all parser tests together**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.parser.*" 2>&1 | tail -20`
Expected: All 4 tests PASS

- [ ] **Step 15: Commit**

```bash
git add src/main/java/com/aihiring/resume/parser/ src/test/java/com/aihiring/resume/parser/ src/test/resources/fixtures/
git commit -m "feat(resume): add text extractors for PDF, DOCX, and TXT with tests"
```

---

## Chunk 4: DTOs, Event & Error Handling

### Task 9: Create DTOs

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/dto/ResumeResponse.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/dto/ResumeListResponse.java`
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/dto/UpdateStructuredRequest.java`

- [ ] **Step 1: Create ResumeResponse**

```java
package com.aihiring.resume.dto;

import com.aihiring.resume.Resume;
import com.aihiring.resume.ResumeSource;
import com.aihiring.resume.ResumeStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ResumeResponse {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String rawText;
    private ResumeSource source;
    private ResumeStatus status;
    private UploadedByInfo uploadedBy;
    private String candidateName;
    private String candidatePhone;
    private String candidateEmail;
    private String education;
    private String experience;
    private String projects;
    private String skills;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @AllArgsConstructor
    public static class UploadedByInfo {
        private UUID id;
        private String username;
    }

    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
            resume.getId(),
            resume.getFileName(),
            resume.getFileSize(),
            resume.getFileType(),
            resume.getRawText(),
            resume.getSource(),
            resume.getStatus(),
            new UploadedByInfo(
                resume.getUploadedBy().getId(),
                resume.getUploadedBy().getUsername()
            ),
            resume.getCandidateName(),
            resume.getCandidatePhone(),
            resume.getCandidateEmail(),
            resume.getEducation(),
            resume.getExperience(),
            resume.getProjects(),
            resume.getSkills(),
            resume.getCreatedAt(),
            resume.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 2: Create ResumeListResponse**

```java
package com.aihiring.resume.dto;

import com.aihiring.resume.Resume;
import com.aihiring.resume.ResumeSource;
import com.aihiring.resume.ResumeStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ResumeListResponse {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String rawTextPreview;
    private ResumeSource source;
    private ResumeStatus status;
    private ResumeResponse.UploadedByInfo uploadedBy;
    private String candidateName;
    private String candidateEmail;
    private LocalDateTime createdAt;

    private static final int RAW_TEXT_PREVIEW_LENGTH = 200;

    public static ResumeListResponse from(Resume resume) {
        String preview = resume.getRawText() == null ? null :
            resume.getRawText().substring(0, Math.min(RAW_TEXT_PREVIEW_LENGTH, resume.getRawText().length()));

        return new ResumeListResponse(
            resume.getId(),
            resume.getFileName(),
            resume.getFileSize(),
            resume.getFileType(),
            preview,
            resume.getSource(),
            resume.getStatus(),
            new ResumeResponse.UploadedByInfo(
                resume.getUploadedBy().getId(),
                resume.getUploadedBy().getUsername()
            ),
            resume.getCandidateName(),
            resume.getCandidateEmail(),
            resume.getCreatedAt()
        );
    }
}
```

- [ ] **Step 3: Create UpdateStructuredRequest**

```java
package com.aihiring.resume.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateStructuredRequest {
    private String candidateName;
    private String candidatePhone;
    private String candidateEmail;
    private String education;   // JSON string
    private String experience;  // JSON string
    private String projects;    // JSON string
    private String skills;      // JSON string
}
```

- [ ] **Step 4: Verify compilation**

Run: `cd ai-hiring-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aihiring/resume/dto/
git commit -m "feat(resume): add ResumeResponse, ResumeListResponse, and UpdateStructuredRequest DTOs"
```

---

### Task 10: Create Event and Add Error Handler

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeUploadedEvent.java`
- Modify: `ai-hiring-backend/src/main/java/com/aihiring/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Create ResumeUploadedEvent**

```java
package com.aihiring.resume;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class ResumeUploadedEvent extends ApplicationEvent {
    private final UUID resumeId;
    private final String rawText;
    private final String fileType;

    public ResumeUploadedEvent(Object source, UUID resumeId, String rawText, String fileType) {
        super(source);
        this.resumeId = resumeId;
        this.rawText = rawText;
        this.fileType = fileType;
    }
}
```

- [ ] **Step 2: Add MaxUploadSizeExceededException handler to GlobalExceptionHandler**

Add this import and method to `GlobalExceptionHandler.java`:

Import:
```java
import org.springframework.web.multipart.MaxUploadSizeExceededException;
```

Methods (add after the existing `handleValidation` method):
```java
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "File size exceeds maximum of 10MB"));
    }
```

Also modify the existing `handleBusiness` method to return the correct HTTP status based on the exception's code (currently it always returns 409 CONFLICT, but `BusinessException(400, ...)` should return 400):

```java
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        HttpStatus httpStatus = HttpStatus.resolve(ex.getCode());
        if (httpStatus == null) httpStatus = HttpStatus.CONFLICT;
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }
```

- [ ] **Step 3: Verify compilation**

Run: `cd ai-hiring-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/aihiring/resume/ResumeUploadedEvent.java src/main/java/com/aihiring/common/exception/GlobalExceptionHandler.java
git commit -m "feat(resume): add ResumeUploadedEvent and MaxUploadSize error handler"
```

---

## Chunk 5: Resume Service (TDD)

### Task 11: ResumeService Unit Tests & Implementation

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeService.java`
- Create: `ai-hiring-backend/src/test/java/com/aihiring/resume/ResumeServiceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package com.aihiring.resume;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.resume.dto.UpdateStructuredRequest;
import com.aihiring.resume.parser.DocxTextExtractor;
import com.aihiring.resume.parser.PdfTextExtractor;
import com.aihiring.resume.parser.TxtTextExtractor;
import com.aihiring.resume.storage.FileStorageService;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceTest {

    @Mock private ResumeRepository resumeRepository;
    @Mock private ResumeSearchRepository resumeSearchRepository;
    @Mock private UserRepository userRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private PdfTextExtractor pdfTextExtractor;
    @Mock private DocxTextExtractor docxTextExtractor;
    @Mock private TxtTextExtractor txtTextExtractor;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ResumeService resumeService;

    @Test
    void upload_withPdf_shouldStoreExtractTextAndSave() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "pdf bytes".getBytes()
        );
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setUsername("admin");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.store(eq(file), any(String.class))).thenReturn("/uploads/resumes/uuid.pdf");
        when(pdfTextExtractor.extract(any())).thenReturn("John Smith Software Engineer");
        when(resumeRepository.save(any(Resume.class))).thenAnswer(i -> {
            Resume r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Resume result = resumeService.upload(file, ResumeSource.MANUAL, userId);

        assertNotNull(result.getId());
        assertEquals("resume.pdf", result.getFileName());
        assertEquals("/uploads/resumes/uuid.pdf", result.getFilePath());
        assertEquals("application/pdf", result.getFileType());
        assertEquals(ResumeStatus.TEXT_EXTRACTED, result.getStatus());
        assertEquals("John Smith Software Engineer", result.getRawText());
        verify(eventPublisher).publishEvent(any(ResumeUploadedEvent.class));
    }

    @Test
    void upload_withTextExtractionFailure_shouldSaveWithUploadedStatus() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", "pdf bytes".getBytes()
        );
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fileStorageService.store(eq(file), any(String.class))).thenReturn("/uploads/resumes/uuid.pdf");
        when(pdfTextExtractor.extract(any())).thenThrow(new IOException("Parse error"));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(i -> {
            Resume r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Resume result = resumeService.upload(file, ResumeSource.MANUAL, userId);

        assertEquals(ResumeStatus.UPLOADED, result.getStatus());
        assertNull(result.getRawText());
        verify(eventPublisher).publishEvent(any(ResumeUploadedEvent.class));
    }

    @Test
    void upload_withEmptyFile_shouldThrowBusinessException() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", new byte[0]
        );
        UUID userId = UUID.randomUUID();

        assertThrows(BusinessException.class, () -> resumeService.upload(file, ResumeSource.MANUAL, userId));
    }

    @Test
    void upload_withUnsupportedType_shouldThrowBusinessException() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.jpg", "image/jpeg", "image bytes".getBytes()
        );
        UUID userId = UUID.randomUUID();

        assertThrows(BusinessException.class, () -> resumeService.upload(file, ResumeSource.MANUAL, userId));
    }

    @Test
    void getById_shouldReturnResume() {
        UUID id = UUID.randomUUID();
        Resume resume = new Resume();
        resume.setId(id);
        when(resumeRepository.findById(id)).thenReturn(Optional.of(resume));

        Resume result = resumeService.getById(id);

        assertEquals(id, result.getId());
    }

    @Test
    void getById_notFound_shouldThrowException() {
        UUID id = UUID.randomUUID();
        when(resumeRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> resumeService.getById(id));
    }

    @Test
    void delete_shouldDeleteFileAndRecord() {
        UUID id = UUID.randomUUID();
        Resume resume = new Resume();
        resume.setId(id);
        resume.setFilePath("/uploads/resumes/uuid.pdf");
        when(resumeRepository.findById(id)).thenReturn(Optional.of(resume));

        resumeService.delete(id);

        verify(fileStorageService).delete("/uploads/resumes/uuid.pdf");
        verify(resumeRepository).delete(resume);
    }

    @Test
    void updateStructured_shouldUpdateFieldsAndSetAiProcessed() {
        UUID id = UUID.randomUUID();
        Resume resume = new Resume();
        resume.setId(id);
        resume.setStatus(ResumeStatus.TEXT_EXTRACTED);
        when(resumeRepository.findById(id)).thenReturn(Optional.of(resume));
        when(resumeRepository.save(any(Resume.class))).thenAnswer(i -> i.getArgument(0));

        UpdateStructuredRequest request = new UpdateStructuredRequest();
        request.setCandidateName("John Smith");
        request.setCandidateEmail("john@example.com");
        request.setSkills("[\"Java\", \"Spring Boot\"]");

        Resume result = resumeService.updateStructured(id, request);

        assertEquals("John Smith", result.getCandidateName());
        assertEquals("john@example.com", result.getCandidateEmail());
        assertEquals("[\"Java\", \"Spring Boot\"]", result.getSkills());
        assertEquals(ResumeStatus.AI_PROCESSED, result.getStatus());
    }

    @Test
    void list_withSearchQuery_shouldDelegateToSearchRepository() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Resume> mockPage = new PageImpl<>(List.of());
        when(resumeSearchRepository.search("Java", pageable)).thenReturn(mockPage);

        Page<Resume> result = resumeService.list("Java", null, null, pageable);

        verify(resumeSearchRepository).search("Java", pageable);
    }

    @Test
    void list_withSourceFilter_shouldFilterBySource() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Resume> mockPage = new PageImpl<>(List.of());
        when(resumeRepository.findBySource(ResumeSource.MANUAL, pageable)).thenReturn(mockPage);

        Page<Resume> result = resumeService.list(null, ResumeSource.MANUAL, null, pageable);

        verify(resumeRepository).findBySource(ResumeSource.MANUAL, pageable);
    }

    @Test
    void list_withNoFilters_shouldReturnAll() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Resume> mockPage = new PageImpl<>(List.of());
        when(resumeRepository.findAll(pageable)).thenReturn(mockPage);

        Page<Resume> result = resumeService.list(null, null, null, pageable);

        verify(resumeRepository).findAll(pageable);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.ResumeServiceTest" 2>&1 | tail -20`
Expected: FAIL — ResumeService not found

- [ ] **Step 3: Create ResumeService**

```java
package com.aihiring.resume;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.resume.dto.UpdateStructuredRequest;
import com.aihiring.resume.parser.DocxTextExtractor;
import com.aihiring.resume.parser.PdfTextExtractor;
import com.aihiring.resume.parser.TextExtractor;
import com.aihiring.resume.parser.TxtTextExtractor;
import com.aihiring.resume.storage.FileStorageService;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeSearchRepository resumeSearchRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final PdfTextExtractor pdfTextExtractor;
    private final DocxTextExtractor docxTextExtractor;
    private final TxtTextExtractor txtTextExtractor;
    private final ApplicationEventPublisher eventPublisher;

    private static final Set<String> ALLOWED_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "text/plain"
    );

    private static final Map<String, String> TYPE_EXTENSIONS = Map.of(
        "application/pdf", "pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx",
        "text/plain", "txt"
    );

    @Transactional
    public Resume upload(MultipartFile file, ResumeSource source, UUID uploadedByUserId) throws IOException {
        validateFile(file);

        User user = userRepository.findById(uploadedByUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String extension = TYPE_EXTENSIONS.get(file.getContentType());
        String storedName = UUID.randomUUID() + "." + extension;
        String storedPath = fileStorageService.store(file, storedName);

        String rawText = null;
        ResumeStatus status = ResumeStatus.UPLOADED;
        try {
            TextExtractor extractor = getExtractor(file.getContentType());
            rawText = extractor.extract(new ByteArrayInputStream(file.getBytes()));
            status = ResumeStatus.TEXT_EXTRACTED;
        } catch (Exception e) {
            log.warn("Text extraction failed for file: {}. Saving with UPLOADED status.", file.getOriginalFilename(), e);
        }

        Resume resume = new Resume();
        resume.setFileName(file.getOriginalFilename());
        resume.setFilePath(storedPath);
        resume.setFileSize(file.getSize());
        resume.setFileType(file.getContentType());
        resume.setRawText(rawText);
        resume.setSource(source);
        resume.setStatus(status);
        resume.setUploadedBy(user);

        resume = resumeRepository.save(resume);

        eventPublisher.publishEvent(new ResumeUploadedEvent(this, resume.getId(), rawText, file.getContentType()));

        return resume;
    }

    public Resume getById(UUID id) {
        return resumeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
    }

    public Page<Resume> list(String search, ResumeSource source, ResumeStatus status, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return resumeSearchRepository.search(search, pageable);
        }
        if (source != null && status != null) {
            return resumeRepository.findBySourceAndStatus(source, status, pageable);
        }
        if (source != null) {
            return resumeRepository.findBySource(source, pageable);
        }
        if (status != null) {
            return resumeRepository.findByStatus(status, pageable);
        }
        return resumeRepository.findAll(pageable);
    }

    @Transactional
    public void delete(UUID id) {
        Resume resume = getById(id);
        fileStorageService.delete(resume.getFilePath());
        resumeRepository.delete(resume);
    }

    @Transactional
    public Resume updateStructured(UUID id, UpdateStructuredRequest request) {
        Resume resume = getById(id);

        if (request.getCandidateName() != null) resume.setCandidateName(request.getCandidateName());
        if (request.getCandidatePhone() != null) resume.setCandidatePhone(request.getCandidatePhone());
        if (request.getCandidateEmail() != null) resume.setCandidateEmail(request.getCandidateEmail());
        if (request.getEducation() != null) resume.setEducation(request.getEducation());
        if (request.getExperience() != null) resume.setExperience(request.getExperience());
        if (request.getProjects() != null) resume.setProjects(request.getProjects());
        if (request.getSkills() != null) resume.setSkills(request.getSkills());

        resume.setStatus(ResumeStatus.AI_PROCESSED);
        return resumeRepository.save(resume);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "File is empty");
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new BusinessException(400, "Unsupported file type. Allowed: PDF, DOCX, TXT");
        }
    }

    private TextExtractor getExtractor(String contentType) {
        return switch (contentType) {
            case "application/pdf" -> pdfTextExtractor;
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> docxTextExtractor;
            case "text/plain" -> txtTextExtractor;
            default -> throw new BusinessException(400, "Unsupported file type");
        };
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.ResumeServiceTest" 2>&1 | tail -20`
Expected: All 11 tests PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/aihiring/resume/ResumeService.java src/test/java/com/aihiring/resume/ResumeServiceTest.java
git commit -m "feat(resume): add ResumeService with upload, list, delete, and updateStructured"
```

---

## Chunk 6: Controller & Integration Tests

### Task 12: ResumeController

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeController.java`

- [ ] **Step 1: Create ResumeController**

```java
package com.aihiring.resume;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.common.security.UserDetailsImpl;
import com.aihiring.resume.dto.ResumeListResponse;
import com.aihiring.resume.dto.ResumeResponse;
import com.aihiring.resume.dto.UpdateStructuredRequest;
import com.aihiring.resume.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('resume:manage')")
    public ApiResponse<ResumeResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "source", defaultValue = "MANUAL") ResumeSource source,
            @AuthenticationPrincipal UserDetailsImpl currentUser) throws IOException {
        Resume resume = resumeService.upload(file, source, currentUser.getId());
        return ApiResponse.success(ResumeResponse.from(resume));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('resume:read')")
    public ApiResponse<Page<ResumeListResponse>> list(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "source", required = false) ResumeSource source,
            @RequestParam(value = "status", required = false) ResumeStatus status,
            Pageable pageable) {
        Page<Resume> page = resumeService.list(search, source, status, pageable);
        return ApiResponse.success(page.map(ResumeListResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('resume:read')")
    public ApiResponse<ResumeResponse> getById(@PathVariable UUID id) {
        Resume resume = resumeService.getById(id);
        return ApiResponse.success(ResumeResponse.from(resume));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('resume:read')")
    public ResponseEntity<Resource> download(@PathVariable UUID id) {
        Resume resume = resumeService.getById(id);
        Resource resource = fileStorageService.load(resume.getFilePath());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resume.getFileType()))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resume.getFileName() + "\"")
            .body(resource);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('resume:manage')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        resumeService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/structured")
    @PreAuthorize("hasAuthority('resume:manage')")
    public ApiResponse<ResumeResponse> updateStructured(
            @PathVariable UUID id,
            @RequestBody UpdateStructuredRequest request) {
        Resume resume = resumeService.updateStructured(id, request);
        return ApiResponse.success(ResumeResponse.from(resume));
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd ai-hiring-backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/aihiring/resume/ResumeController.java
git commit -m "feat(resume): add ResumeController with all endpoints"
```

---

### Task 13: Integration Tests

**Files:**
- Create: `ai-hiring-backend/src/test/java/com/aihiring/resume/ResumeControllerIntegrationTest.java`

- [ ] **Step 1: Write integration tests**

```java
package com.aihiring.resume;

import com.aihiring.common.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor adminUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "admin", "password", true, null,
            List.of("SUPER_ADMIN"),
            List.of("resume:read", "resume:manage", "user:read", "user:manage")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor readOnlyUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "viewer", "password", true, null,
            List.of("USER"),
            List.of("resume:read")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    @Test
    void upload_withValidPdf_shouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain", "John Smith\nSoftware Engineer\nJava Spring".getBytes()
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("resume.txt"))
                .andExpect(jsonPath("$.data.status").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.rawText").exists());
    }

    @Test
    void upload_withEmptyFile_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.txt", "text/plain", new byte[0]
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_withUnsupportedType_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "image.jpg", "image/jpeg", "fake image".getBytes()
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_withoutPermission_shouldReturn403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain", "content".getBytes()
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(readOnlyUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_shouldReturnPaginatedResults() throws Exception {
        // Upload a file first
        MockMultipartFile file = new MockMultipartFile(
            "file", "list-test.txt", "text/plain", "Test content for listing".getBytes()
        );
        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()));

        mockMvc.perform(get("/api/resumes")
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getById_withValidId_shouldReturnResume() throws Exception {
        // Upload a file first
        MockMultipartFile file = new MockMultipartFile(
            "file", "detail-test.txt", "text/plain", "Detail test content".getBytes()
        );
        MvcResult uploadResult = mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        // Extract ID from response using simple string parsing
        String id = responseBody.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/resumes/" + id)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("detail-test.txt"));
    }

    @Test
    void getById_withInvalidId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/resumes/" + UUID.randomUUID())
                .with(adminUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldRemoveResume() throws Exception {
        // Upload a file first
        MockMultipartFile file = new MockMultipartFile(
            "file", "delete-test.txt", "text/plain", "Delete test content".getBytes()
        );
        MvcResult uploadResult = mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        String id = responseBody.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(delete("/api/resumes/" + id)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Verify it's gone
        mockMvc.perform(get("/api/resumes/" + id)
                .with(adminUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_shouldReturn401Or403() throws Exception {
        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isForbidden());
    }
}
```

Note: The `upload_withEmptyFile` and `upload_withUnsupportedType` tests expect `status().isBadRequest()` because `GlobalExceptionHandler.handleBusiness()` now resolves the HTTP status from `BusinessException.getCode()`. A `BusinessException(400, ...)` returns HTTP 400.

- [ ] **Step 2: Run integration tests**

Run: `cd ai-hiring-backend && ./gradlew test --tests "com.aihiring.resume.ResumeControllerIntegrationTest" 2>&1 | tail -30`
Expected: All tests PASS

If there are failures, investigate and fix. Common issues:
- H2 schema not loading: check `schema-h2.sql` path and `application-test.yml` config
- User not found: the `DataInitializer` should create the admin user with ID `04000000-0000-0000-0000-000000000001` during test startup — check if the ID matches. If `DataInitializer` generates random UUIDs, the test must use whatever ID is created, or the test should mock the security context more carefully.
- Enum mapping: H2 uses VARCHAR for enums, so `@Enumerated(EnumType.STRING)` is required (already set).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/aihiring/resume/ResumeControllerIntegrationTest.java
git commit -m "test(resume): add ResumeController integration tests"
```

---

### Task 14: Run Full Test Suite

- [ ] **Step 1: Run all tests**

Run: `cd ai-hiring-backend && ./gradlew test 2>&1 | tail -30`
Expected: All tests PASS (both existing user/auth tests and new resume tests)

- [ ] **Step 2: Fix any failures**

If any tests fail, investigate and fix. The most likely issues are:
- H2 schema conflicts between existing entities and the new `resumes` table
- Spring context loading issues with the new `@Profile` beans
- `DataInitializer` conflicts with test data

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat(resume): complete resume module with upload, storage, text extraction, and search"
```

---

## Plan Complete

**Plan complete and saved to `docs/superpowers/plans/2026-03-18-resume-module.md`. Ready to execute?**

This plan covers the Resume Module implementation with ~14 TDD tasks across 6 chunks:
1. Dependencies, Configuration & Database Migration
2. Entity, Enums & Repository
3. File Storage & Text Extraction
4. DTOs, Event & Error Handling
5. Resume Service (TDD)
6. Controller & Integration Tests
