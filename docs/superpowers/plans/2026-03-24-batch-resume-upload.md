# Batch Resume Upload Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow users to upload multiple resumes at once from the resume list page, each processed independently.

**Architecture:** Reuse existing `/api/resumes/upload` endpoint, extended to accept `MultipartFile[]`. Each file goes through the same pipeline (validate → store → text extract → event publish). Controller returns `BatchUploadResponse` with per-file results. Frontend adds a batch upload modal to the resume list page.

**Tech Stack:** Spring Boot (backend), React + Ant Design (frontend), JUnit + MockMvc (tests), Vitest (frontend tests)

---

## File Map

### Backend
```
ai-hiring-backend/src/main/java/com/aihiring/resume/
  ├── dto/
  │   ├── BatchUploadResponse.java   [CREATE]
  │   └── BatchUploadResult.java     [CREATE]
  └── ResumeService.java             [MODIFY - extract uploadSingle, add size validation]
ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeController.java [MODIFY - accept both file and files params]
ai-hiring-backend/src/test/java/com/aihiring/resume/
  ├── ResumeServiceTest.java          [MODIFY - add uploadSingle tests, size validation tests]
  └── ResumeControllerIntegrationTest.java [MODIFY - add batch upload integration tests]
```

### Frontend
```
frontend/src/
  ├── api/resumes.ts                 [MODIFY - add uploadResumes()]
  ├── api/resumes.test.ts            [MODIFY - add uploadResumes tests]
  ├── pages/resumes/
  │   ├── BatchUploadModal.tsx       [CREATE]
  │   ├── BatchUploadModal.test.tsx  [CREATE]
  │   └── ResumeListPage.tsx         [MODIFY - add batch upload button and modal]
```

---

## Chunk 1: Backend DTOs

### Task 1: Create BatchUploadResult DTO

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/dto/BatchUploadResult.java`

- [ ] **Step 1: Write the failing test**

```java
package com.aihiring.resume.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchUploadResultTest {
    @Test
    void constructor_andGetters_shouldWork() {
        BatchUploadResult result = new BatchUploadResult(0, "resume.pdf", "UPLOADED", UUID.randomUUID(), null);
        assertEquals(0, result.getOriginalIndex());
        assertEquals("resume.pdf", result.getFileName());
        assertEquals("UPLOADED", result.getStatus());
        assertNotNull(result.getResumeId());
        assertNull(result.getError());
    }

    @Test
    void failedResult_shouldHaveError() {
        BatchUploadResult result = new BatchUploadResult(1, "bad.exe", "FAILED", null, "Unsupported file type");
        assertEquals("FAILED", result.getStatus());
        assertEquals("Unsupported file type", result.getError());
        assertNull(result.getResumeId());
    }
}
```

Run: `./mvnw test -Dtest=BatchUploadResultTest -q`
Expected: FAIL (class does not exist)

- [ ] **Step 2: Write the implementation**

```java
package com.aihiring.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class BatchUploadResult {
    private int originalIndex;
    private String fileName;
    private String status;       // "UPLOADED" or "FAILED"
    private UUID resumeId;       // null if failed
    private String error;        // null if succeeded
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./mvnw test -Dtest=BatchUploadResultTest -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ai-hiring-backend/src/main/java/com/aihiring/resume/dto/BatchUploadResult.java
git add ai-hiring-backend/src/test/java/com/aihiring/resume/dto/BatchUploadResultTest.java
git commit -m "feat(backend): add BatchUploadResult DTO"
```

---

### Task 2: Create BatchUploadResponse DTO

**Files:**
- Create: `ai-hiring-backend/src/main/java/com/aihiring/resume/dto/BatchUploadResponse.java`

- [ ] **Step 1: Write the failing test**

```java
package com.aihiring.resume.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchUploadResponseTest {
    @Test
    void constructor_shouldComputeSucceededAndFailed() {
        List<BatchUploadResult> results = List.of(
            new BatchUploadResult(0, "a.pdf", "UPLOADED", UUID.randomUUID(), null),
            new BatchUploadResult(1, "b.pdf", "FAILED", null, "bad type"),
            new BatchUploadResult(2, "c.pdf", "UPLOADED", UUID.randomUUID(), null)
        );
        BatchUploadResponse response = new BatchUploadResponse(results);
        assertEquals(3, response.getTotal());
        assertEquals(2, response.getSucceeded());
        assertEquals(1, response.getFailed());
        assertEquals(3, response.getResults().size());
    }

    @Test
    void emptyResults_shouldHaveZeroCounts() {
        BatchUploadResponse response = new BatchUploadResponse(List.of());
        assertEquals(0, response.getTotal());
        assertEquals(0, response.getSucceeded());
        assertEquals(0, response.getFailed());
    }
}
```

Run: `./mvnw test -Dtest=BatchUploadResponseTest -q`
Expected: FAIL (class does not exist)

- [ ] **Step 2: Write the implementation**

```java
package com.aihiring.resume.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class BatchUploadResponse {
    private final int total;
    private final int succeeded;
    private final int failed;
    private final List<BatchUploadResult> results;

    public BatchUploadResponse(List<BatchUploadResult> results) {
        this.results = results;
        this.total = results.size();
        this.succeeded = (int) results.stream().filter(r -> "UPLOADED".equals(r.getStatus())).count();
        this.failed = (int) results.stream().filter(r -> "FAILED".equals(r.getStatus())).count();
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./mvnw test -Dtest=BatchUploadResponseTest -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ai-hiring-backend/src/main/java/com/aihiring/resume/dto/BatchUploadResponse.java
git add ai-hiring-backend/src/test/java/com/aihiring/resume/dto/BatchUploadResponseTest.java
git commit -m "feat(backend): add BatchUploadResponse DTO"
```

---

## Chunk 2: Backend Service

### Task 3: Add 10MB size validation to validateFile()

**Files:**
- Modify: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeService.java:131-138`

- [ ] **Step 1: Write the failing test**

In `ResumeServiceTest.java`, add:

```java
@Test
void upload_withFileOver10MB_shouldThrowBusinessException() {
    byte[] bigContent = new byte[11 * 1024 * 1024]; // 11MB
    MockMultipartFile file = new MockMultipartFile("file", "big.pdf", "application/pdf", bigContent);
    BusinessException ex = assertThrows(BusinessException.class,
        () -> resumeService.upload(file, ResumeSource.MANUAL, UUID.randomUUID()));
    assertEquals(400, ex.getCode());
    assertTrue(ex.getMessage().contains("10MB"));
}
```

Run: `./mvnw test -Dtest=ResumeServiceTest#upload_withFileOver10MB_shouldThrowBusinessException -q`
Expected: FAIL (no 10MB check in validateFile yet)

- [ ] **Step 2: Add 10MB size check to validateFile()**

Modify `validateFile()` in `ResumeService.java`:

```java
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

private void validateFile(MultipartFile file) {
    if (file.isEmpty()) {
        throw new BusinessException(400, "File is empty");
    }
    if (!ALLOWED_TYPES.contains(file.getContentType())) {
        throw new BusinessException(400, "Unsupported file type. Allowed: PDF, DOCX, TXT");
    }
    if (file.getSize() > MAX_FILE_SIZE) {
        throw new BusinessException(400, "File size exceeds 10MB limit");
    }
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./mvnw test -Dtest=ResumeServiceTest#upload_withFileOver10MB_shouldThrowBusinessException -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeService.java
git commit -m "feat(backend): add 10MB per-file size validation in validateFile()"
```

---

### Task 4: Extract uploadSingle() from upload()

**Files:**
- Modify: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeService.java`

- [ ] **Step 1: Write the failing test**

In `ResumeServiceTest.java`, add a test that calls a hypothetical `uploadSingle`:

```java
@Test
void uploadSingle_withValidFile_shouldReturnResume() throws IOException {
    MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "pdf bytes".getBytes());
    UUID userId = UUID.randomUUID();
    User user = new User(); user.setId(userId); user.setUsername("admin");

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(fileStorageService.store(eq(file), any(String.class))).thenReturn("/uploads/resumes/uuid.pdf");
    when(pdfTextExtractor.extract(any())).thenReturn("John Smith");
    when(resumeRepository.save(any(Resume.class))).thenAnswer(i -> { Resume r = i.getArgument(0); r.setId(UUID.randomUUID()); return r; });

    Resume result = resumeService.uploadSingle(file, ResumeSource.MANUAL, userId);

    assertNotNull(result);
    assertEquals("resume.pdf", result.getFileName());
    verify(eventPublisher).publishEvent(any(ResumeUploadedEvent.class));
}
```

Run: `./mvnw test -Dtest=ResumeServiceTest#uploadSingle_withValidFile_shouldReturnResume -q`
Expected: FAIL (method doesn't exist yet)

- [ ] **Step 2: Extract uploadSingle() method**

In `ResumeService.java`, add `uploadSingle()` method and refactor `upload()` to use it:

```java
@Transactional
public Resume uploadSingle(MultipartFile file, ResumeSource source, UUID uploadedByUserId) throws IOException {
    validateFile(file);

    var user = userRepository.findById(uploadedByUserId)
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

@Transactional
public Resume upload(MultipartFile file, ResumeSource source, UUID uploadedByUserId) throws IOException {
    return uploadSingle(file, source, uploadedByUserId);
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `./mvnw test -Dtest=ResumeServiceTest#uploadSingle_withValidFile_shouldReturnResume -q`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeService.java
git commit -m "feat(backend): extract uploadSingle() from upload() for batch support"
```

---

## Chunk 3: Backend Controller

### Task 5: Update ResumeController to accept both file and files parameters

**Files:**
- Modify: `ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeController.java:33-41`

- [ ] **Step 1: Write the failing test**

In `ResumeControllerIntegrationTest.java`, add:

```java
@Test
void upload_withMultipleFiles_shouldReturnBatchResponse() throws Exception {
    MockMultipartFile file1 = new MockMultipartFile("files", "resume1.txt", "text/plain", "John Smith\nEngineer".getBytes());
    MockMultipartFile file2 = new MockMultipartFile("files", "resume2.txt", "text/plain", "Jane Doe\nManager".getBytes());

    mockMvc.perform(multipart("/api/resumes/upload")
            .file(file1)
            .file(file2)
            .with(adminUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.succeeded").value(2))
            .andExpect(jsonPath("$.data.failed").value(0))
            .andExpect(jsonPath("$.data.results[0].fileName").value("resume1.txt"))
            .andExpect(jsonPath("$.data.results[0].status").value("TEXT_EXTRACTED"))
            .andExpect(jsonPath("$.data.results[1].fileName").value("resume2.txt"))
            .andExpect(jsonPath("$.data.results[1].status").value("TEXT_EXTRACTED"));
}

@Test
void upload_withNoFilesParam_shouldReturn400() throws Exception {
    // No 'file' and no 'files' param — Controller should return 400
    mockMvc.perform(multipart("/api/resumes/upload")
            .with(adminUser()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
}

@Test
void upload_withSingleFileUsingFilesParam_shouldWork() throws Exception {
    MockMultipartFile file = new MockMultipartFile("files", "single.txt", "text/plain", "content".getBytes());

    mockMvc.perform(multipart("/api/resumes/upload")
            .file(file)
            .with(adminUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));
}

@Test
void upload_withExceeds100Files_shouldReturn400() throws Exception {
    List<MockMultipartFile> files = IntStream.range(0, 101)
        .mapToObj(i -> new MockMultipartFile("files", "resume" + i + ".txt", "text/plain", ("content" + i).getBytes()))
        .toList();

    ResultActions actions = mockMvc.perform(multipart("/api/resumes/upload")
            .file(files.toArray(new MockMultipartFile[0]))
            .with(adminUser()));
    actions.andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(400));
}

@Test
void upload_withTotalSizeExceeds200MB_shouldReturn400() throws Exception {
    // Create files that together exceed 200MB
    byte[] bigChunk = new byte[1024 * 1024]; // 1MB
    List<MockMultipartFile> files = IntStream.range(0, 201)
        .mapToObj(i -> new MockMultipartFile("files", "resume" + i + ".txt", "text/plain", bigChunk))
        .toList();

    ResultActions actions = mockMvc.perform(multipart("/api/resumes/upload")
            .file(files.toArray(new MockMultipartFile[0]))
            .with(adminUser()));
    actions.andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value(400));
}

@Test
void upload_withPartialFailure_shouldReturn200WithFailedEntries() throws Exception {
    // File 1: valid, File 2: invalid type, File 3: valid
    MockMultipartFile good1 = new MockMultipartFile("files", "good1.txt", "text/plain", "valid content".getBytes());
    MockMultipartFile bad = new MockMultipartFile("files", "bad.jpg", "image/jpeg", "not a resume".getBytes());
    MockMultipartFile good2 = new MockMultipartFile("files", "good2.txt", "text/plain", "also valid".getBytes());

    mockMvc.perform(multipart("/api/resumes/upload")
            .file(good1)
            .file(bad)
            .file(good2)
            .with(adminUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(3))
            .andExpect(jsonPath("$.data.succeeded").value(2))
            .andExpect(jsonPath("$.data.failed").value(1))
            .andExpect(jsonPath("$.data.results[0].fileName").value("good1.txt"))
            .andExpect(jsonPath("$.data.results[0].status").value("TEXT_EXTRACTED"))
            .andExpect(jsonPath("$.data.results[1].fileName").value("bad.jpg"))
            .andExpect(jsonPath("$.data.results[1].status").value("FAILED"))
            .andExpect(jsonPath("$.data.results[1].error").exists())
            .andExpect(jsonPath("$.data.results[2].fileName").value("good2.txt"))
            .andExpect(jsonPath("$.data.results[2].status").value("TEXT_EXTRACTED"));
}

@Test
void upload_withOneFileOver10MBInBatch_shouldFailOnlyThatFile() throws Exception {
    byte[] normalContent = "normal resume".getBytes();
    byte[] bigContent = new byte[11 * 1024 * 1024]; // 11MB - exceeds limit
    MockMultipartFile good = new MockMultipartFile("files", "good.txt", "text/plain", normalContent);
    MockMultipartFile tooBig = new MockMultipartFile("files", "big.pdf", "application/pdf", bigContent);

    mockMvc.perform(multipart("/api/resumes/upload")
            .file(good)
            .file(tooBig)
            .with(adminUser()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.succeeded").value(1))
            .andExpect(jsonPath("$.data.failed").value(1))
            .andExpect(jsonPath("$.data.results[0].status").value("TEXT_EXTRACTED"))
            .andExpect(jsonPath("$.data.results[1].status").value("FAILED"))
            .andExpect(jsonPath("$.data.results[1].error").value("File size exceeds 10MB limit"));
}
```

Run: `./mvnw test -Dtest=ResumeControllerIntegrationTest#upload_withMultipleFiles_shouldReturnBatchResponse -q`
Expected: FAIL (endpoint doesn't handle arrays yet)

- [ ] **Step 2: Update ResumeController**

Replace the `upload()` method in `ResumeController.java`:

```java
@PostMapping("/upload")
@PreAuthorize("hasAuthority('resume:manage')")
public ApiResponse<BatchUploadResponse> upload(
        @RequestParam(value = "files", required = false) MultipartFile[] files,
        @RequestParam(value = "file", required = false) MultipartFile singleFile,
        @RequestParam(value = "source", defaultValue = "MANUAL") ResumeSource source,
        @AuthenticationPrincipal UserDetailsImpl currentUser) throws IOException {

    // Handle single file backward compat (existing 'file' param)
    if (files == null || files.length == 0) {
        if (singleFile == null || singleFile.isEmpty()) {
            return ApiResponse.error(400, "No file provided");
        }
        // Wrap single file in array for uniform processing
        files = new MultipartFile[] { singleFile };
    }

    // Batch limits validation
    if (files.length > 100) {
        return ApiResponse.error(400, "Batch size exceeds 100 files limit");
    }
    long totalSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
    if (totalSize > 200 * 1024 * 1024) {
        return ApiResponse.error(400, "Total batch size exceeds 200MB limit");
    }

    List<BatchUploadResult> results = new ArrayList<>();
    for (int i = 0; i < files.length; i++) {
        MultipartFile file = files[i];
        try {
            Resume resume = resumeService.uploadSingle(file, source, currentUser.getId());
            results.add(new BatchUploadResult(i, file.getOriginalFilename(), "UPLOADED", resume.getId(), null));
        } catch (BusinessException e) {
            results.add(new BatchUploadResult(i, file.getOriginalFilename(), "FAILED", null, e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error processing file: {}", file.getOriginalFilename(), e);
            results.add(new BatchUploadResult(i, file.getOriginalFilename(), "FAILED", null, "Internal server error"));
        }
    }

    return ApiResponse.success(new BatchUploadResponse(results));
}
```

Add imports:
```java
import com.aihiring.resume.dto.BatchUploadResponse;
import com.aihiring.resume.dto.BatchUploadResult;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `./mvnw test -Dtest=ResumeControllerIntegrationTest#upload_withMultipleFiles_shouldReturnBatchResponse,ResumeControllerIntegrationTest#upload_withSingleFileUsingFilesParam,ResumeControllerIntegrationTest#upload_withExceeds100Files_shouldReturn400,ResumeControllerIntegrationTest#upload_withTotalSizeExceeds200MB_shouldReturn400,ResumeControllerIntegrationTest#upload_withPartialFailure_shouldReturn200WithFailedEntries,ResumeControllerIntegrationTest#upload_withOneFileOver10MBInBatch_shouldFailOnlyThatFile -q`
Expected: All PASS

- [ ] **Step 4: Commit**

```bash
git add ai-hiring-backend/src/main/java/com/aihiring/resume/ResumeController.java
git add ai-hiring-backend/src/test/java/com/aihiring/resume/ResumeControllerIntegrationTest.java
git commit -m "feat(backend): support batch resume upload with file/files params"
```

---

## Chunk 4: Frontend API

### Task 6: Add uploadResumes() to frontend API

**Files:**
- Modify: `frontend/src/api/resumes.ts`
- Modify: `frontend/src/api/resumes.test.ts`

- [ ] **Step 1: Write the failing test**

In `resumes.test.ts`:

```typescript
it('uploadResumes() POSTs multiple files and returns batch response', async () => {
  const mockResponse = {
    code: 200, message: 'ok',
    data: {
      total: 2, succeeded: 2, failed: 0,
      results: [
        { originalIndex: 0, fileName: 'a.pdf', status: 'UPLOADED', resumeId: 'r1', error: null },
        { originalIndex: 1, fileName: 'b.pdf', status: 'UPLOADED', resumeId: 'r2', error: null },
      ]
    }
  };
  vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
    ok: true, status: 200, json: () => Promise.resolve(mockResponse),
  } as Response);
  const { uploadResumes } = await import('./resumes');
  const files = [
    new File(['a'], 'a.pdf', { type: 'application/pdf' }),
    new File(['b'], 'b.pdf', { type: 'application/pdf' }),
  ];
  const result = await uploadResumes(files);
  expect(result.total).toBe(2);
  expect(result.succeeded).toBe(2);
  expect(result.results[0].fileName).toBe('a.pdf');
  const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0] as [string, RequestInit];
  expect(url).toBe('/api/resumes/upload?source=MANUAL');
  expect(init.method).toBe('POST');
});
```

Run: `cd frontend && npx vitest run src/api/resumes.test.ts -t "uploadResumes"`
Expected: FAIL (function doesn't exist)

- [ ] **Step 2: Add uploadResumes() to resumes.ts**

```typescript
export interface BatchUploadResult {
  originalIndex: number;
  fileName: string;
  status: string;
  resumeId: string | null;
  error: string | null;
}

export interface BatchUploadResponse {
  total: number;
  succeeded: number;
  failed: number;
  results: BatchUploadResult[];
}

export async function uploadResumes(files: File[]): Promise<BatchUploadResponse> {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await fetch('/api/resumes/upload?source=MANUAL', {
    method: 'POST',
    headers,
    body: formData,
  });
  const body = await response.json();
  if (response.ok) return body.data;
  throw new Error(body.message ?? `HTTP ${response.status}`);
}
```

- [ ] **Step 3: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/api/resumes.test.ts -t "uploadResumes"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/resumes.ts frontend/src/api/resumes.test.ts
git commit -m "feat(frontend): add uploadResumes() batch API function"
```

---

## Chunk 5: Frontend BatchUploadModal Component

### Task 7: Create BatchUploadModal component

**Files:**
- Create: `frontend/src/pages/resumes/BatchUploadModal.tsx`
- Create: `frontend/src/pages/resumes/BatchUploadModal.test.tsx`

- [ ] **Step 1: Write the failing test**

In `BatchUploadModal.test.tsx`:

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
// Note: Ant Design Upload.Dragger may need custom testing setup.
// Adjust tests based on actual Ant Design test behavior.
import { BatchUploadModal } from './BatchUploadModal';

describe('BatchUploadModal', () => {
  const mockOnClose = vi.fn();
  const mockOnSuccess = vi.fn();

  beforeEach(() => { vi.clearAllMocks(); });

  it('renders drag-drop area and file list', () => {
    render(<BatchUploadModal open onClose={mockOnClose} onSuccess={mockOnSuccess} />);
    expect(screen.getByText('Batch Upload Resumes')).toBeInTheDocument();
    expect(screen.getByText('Click or drag files to upload')).toBeInTheDocument();
  });

  it('shows selected files in list', async () => {
    render(<BatchUploadModal open onClose={mockOnClose} onSuccess={mockOnSuccess} />);
    const file = new File(['content'], 'resume.pdf', { type: 'application/pdf' });
    // Ant Design Upload passes the File via originFileObj in onChange
    const mockFileList = [{
      uid: '1',
      name: 'resume.pdf',
      originFileObj: file,
    }];
    const uploadChangeHandler = screen.getByTestId('batch-file-input');
    // Ant Design Upload.Dragger's onChange is called with a custom event-like object
    fireEvent(uploadChangeHandler, { target: { files: [file] } });
    // The Ant Design Upload component handles files differently; test via the Dragger's internal mechanism
    expect(await screen.findByText('resume.pdf')).toBeInTheDocument();
  });

  it('removes file from list when remove clicked', async () => {
    render(<BatchUploadModal open onClose={mockOnClose} onSuccess={mockOnSuccess} />);
    const file = new File(['content'], 'resume.pdf', { type: 'application/pdf' });
    fireEvent(screen.getByTestId('batch-file-input'), { target: { files: [file] } });
    expect(await screen.findByText('resume.pdf')).toBeInTheDocument();
    const removeBtn = screen.getByRole('button', { name: /remove/i });
    await userEvent.click(removeBtn);
    expect(screen.queryByText('resume.pdf')).not.toBeInTheDocument();
  });
});
```

Run: `cd frontend && npx vitest run src/pages/resumes/BatchUploadModal.test.tsx`
Expected: FAIL (component doesn't exist)

- [ ] **Step 2: Create BatchUploadModal.tsx**

```typescript
import { useState } from 'react';
import { Modal, Upload, List, Button, message } from 'antd';
import { UploadOutlined, DeleteOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { uploadResumes, type BatchUploadResult } from '../../api/resumes';

interface BatchUploadModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

interface FileItem {
  uid: string;
  file: File;  // Store actual File object, not just metadata
  status: 'pending' | 'uploading' | 'done' | 'error';
  error?: string;
  result?: BatchUploadResult;
}

export function BatchUploadModal({ open, onClose, onSuccess }: BatchUploadModalProps) {
  const [files, setFiles] = useState<FileItem[]>([]);
  const [uploading, setUploading] = useState(false);

  const handleFileChange: UploadProps['onChange'] = (info) => {
    const newFiles: FileItem[] = info.fileList
      .filter((fl) => fl.originFileObj instanceof File)
      .map((fl) => ({
        uid: fl.uid,
        file: fl.originFileObj as File,
        status: 'pending' as const,
      }));
    setFiles((prev) => {
      const existingIds = new Set(prev.map((f) => f.uid));
      const filtered = prev.filter((f) => !newFiles.some((nf) => nf.uid === f.uid) ||
        newFiles.some((nf) => nf.uid === f.uid && nf.file !== prev.find((pf) => pf.uid === nf.uid)?.file));
      return [...filtered, ...newFiles.filter((nf) => !existingIds.has(nf.uid))];
    });
  };

  const handleRemove = (uid: string) => {
    setFiles((prev) => prev.filter((f) => f.uid !== uid));
  };

  const handleUpload = async () => {
    if (files.length === 0) return;
    setUploading(true);
    setFiles((prev) => prev.map((f) => ({ ...f, status: 'uploading' as const })));

    try {
      const actualFiles = files.map((f) => f.file);
      const result = await uploadResumes(actualFiles);
      const resultMap = new Map(result.results.map((r) => [r.fileName, r]));
      setFiles((prev) =>
        prev.map((f) => {
          const r = resultMap.get(f.file.name);
          if (!r) return { ...f, status: 'error' as const, error: 'No result returned' };
          return {
            ...f,
            status: r.status === 'UPLOADED' ? ('done' as const) : ('error' as const),
            error: r.error ?? undefined,
            result: r,
          };
        })
      );
      message.success(`Uploaded ${result.succeeded}/${result.total} resumes`);
      if (result.succeeded > 0) onSuccess();
    } catch (err) {
      message.error('Upload failed: ' + (err as Error).message);
      setFiles((prev) => prev.map((f) => ({ ...f, status: 'error' as const, error: String(err) })));
    } finally {
      setUploading(false);
    }
  };

  const handleClose = () => {
    if (!uploading) {
      setFiles([]);
      onClose();
    }
  };

  return (
    <Modal
      title="Batch Upload Resumes"
      open={open}
      onCancel={handleClose}
      footer={[
        <Button key="cancel" onClick={handleClose} disabled={uploading}>Cancel</Button>,
        <Button
          key="upload"
          type="primary"
          loading={uploading}
          disabled={files.length === 0}
          onClick={handleUpload}
        >
          Upload {files.length > 0 ? `(${files.length})` : ''}
        </Button>,
      ]}
    >
      <Upload.Dragger
        multiple
        showUploadList={false}
        accept=".pdf,.doc,.docx,.txt"
        onChange={handleFileChange}
        beforeUpload={() => false}
        data-testid="batch-file-input"
      >
        <p><UploadOutlined /></p>
        <p>Click or drag files to upload</p>
        <p style={{ fontSize: 12, color: '#999' }}>PDF, DOC, DOCX, TXT • Max 10MB per file</p>
      </Upload.Dragger>

      {files.length > 0 && (
        <List
          style={{ marginTop: 16, maxHeight: 300, overflow: 'auto' }}
          dataSource={files}
          renderItem={(item) => (
            <List.Item
              key={item.uid}
              actions={[
                <DeleteOutlined key="remove" onClick={() => handleRemove(item.uid)} />,
              ]}
            >
              <List.Item.Meta
                title={item.file.name}
                description={
                  item.status === 'error' ? (
                    <span style={{ color: 'red' }}>{item.error}</span>
                  ) : item.status === 'done' ? (
                    <span style={{ color: 'green' }}>Uploaded</span>
                  ) : item.status === 'uploading' ? (
                    <span style={{ color: 'blue' }}>Uploading...</span>
                  ) : (
                    <span style={{ color: '#999' }}>Ready</span>
                  )
                }
              />
            </List.Item>
          )}
        />
      )}
    </Modal>
  );
}

- [ ] **Step 3: Run tests and fix issues**

The test structure above may need adjustment based on Ant Design's `Upload` component testing. Verify tests pass.

Run: `cd frontend && npx vitest run src/pages/resumes/BatchUploadModal.test.tsx`
Expected: PASS (or adjust test to match component implementation)

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/resumes/BatchUploadModal.tsx frontend/src/pages/resumes/BatchUploadModal.test.tsx
git commit -m "feat(frontend): add BatchUploadModal component"
```

---

## Chunk 6: Frontend Integration

### Task 8: Integrate BatchUploadModal into ResumeListPage

**Files:**
- Modify: `frontend/src/pages/resumes/ResumeListPage.tsx`
- Modify: `frontend/src/pages/resumes/ResumeListPage.test.tsx`

- [ ] **Step 1: Write the failing test**

In `ResumeListPage.test.tsx`, add:

```typescript
it('should show batch upload button', () => {
  render(<ResumeListPage />);
  expect(screen.getByText('Batch Upload')).toBeInTheDocument();
});
```

Run: `cd frontend && npx vitest run src/pages/resumes/ResumeListPage.test.tsx -t "batch upload button"`
Expected: FAIL (button doesn't exist yet)

- [ ] **Step 2: Add batch upload button and modal to ResumeListPage**

In `ResumeListPage.tsx`:

1. Import `BatchUploadModal` and `useState`:
```typescript
import { useEffect, useState } from 'react';
import { Table, Button, Tag, Popconfirm, Space, message, Empty, Modal } from 'antd';
import { useNavigate } from 'react-router-dom';
import { listResumes, deleteResume, downloadResume, type ResumeListItem, type Page } from '../../api/resumes';
import { BatchUploadModal } from './BatchUploadModal';
```

2. Add state and handlers:
```typescript
const [batchModalOpen, setBatchModalOpen] = useState(false);

const handleBatchSuccess = () => {
  load(current);
  setBatchModalOpen(false);
};
```

3. Add button in toolbar:
```typescript
<Button onClick={() => setBatchModalOpen(true)}>Batch Upload</Button>
```

4. Add modal at end of JSX:
```typescript
<BatchUploadModal
  open={batchModalOpen}
  onClose={() => setBatchModalOpen(false)}
  onSuccess={handleBatchSuccess}
/>
```

- [ ] **Step 3: Run test to verify it passes**

Run: `cd frontend && npx vitest run src/pages/resumes/ResumeListPage.test.tsx -t "batch upload button"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/resumes/ResumeListPage.tsx frontend/src/pages/resumes/ResumeListPage.test.tsx
git commit -m "feat(frontend): integrate BatchUploadModal into ResumeListPage"
```

---

## Chunk 7: Final Verification

### Task 9: Run full backend test suite

- [ ] **Step 1: Run all resume-related tests**

Run: `cd ai-hiring-backend && ./mvnw test -Dtest="ResumeServiceTest,ResumeControllerIntegrationTest,BatchUploadResultTest,BatchUploadResponseTest" -q`
Expected: ALL PASS

### Task 10: Run full frontend test suite

- [ ] **Step 1: Run frontend tests**

Run: `cd frontend && npx vitest run src/api/resumes.test.ts src/pages/resumes/`
Expected: ALL PASS

### Task 11: Build verification

- [ ] **Step 1: Build backend**

Run: `cd ai-hiring-backend && ./mvnw package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 2: Build frontend**

Run: `cd frontend && npm run build`
Expected: No errors
