package com.aihiring.resume;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.security.UserDetailsImpl;
import com.aihiring.resume.dto.BatchUploadResponse;
import com.aihiring.resume.dto.BatchUploadResult;
import com.aihiring.resume.dto.ResumeListResponse;
import com.aihiring.resume.dto.ResumeResponse;
import com.aihiring.resume.dto.UpdateStructuredRequest;
import com.aihiring.resume.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;
    private final FileStorageService fileStorageService;

    @PostMapping("/upload")
    @PreAuthorize("hasAuthority('resume:manage')")
    public ResponseEntity<ApiResponse<?>> upload(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "file", required = false) MultipartFile singleFile,
            @RequestParam(value = "source", defaultValue = "MANUAL") ResumeSource source,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {

        // Handle single file backward compat (existing 'file' param)
        if (files == null || files.length == 0) {
            if (singleFile == null || singleFile.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "No file provided"));
            }
            files = new MultipartFile[] { singleFile };
        }

        // Batch limits validation
        if (files.length > 100) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Batch size exceeds 100 files limit"));
        }
        long totalSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        if (totalSize > 200 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "Total batch size exceeds 200MB limit"));
        }

        // For single file, use original response format for backward compatibility
        if (files.length == 1) {
            try {
                Resume resume = resumeService.uploadSingle(files[0], source, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success(ResumeResponse.from(resume)));
            } catch (BusinessException e) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, e.getMessage()));
            } catch (IOException e) {
                log.error("File upload error", e);
                return ResponseEntity.status(500)
                    .body(ApiResponse.error(500, "File upload failed: " + e.getMessage()));
            }
        }

        // For multiple files, use batch response
        List<BatchUploadResult> results = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            try {
                Resume resume = resumeService.uploadSingle(file, source, currentUser.getId());
                results.add(new BatchUploadResult(i, file.getOriginalFilename(), resume.getStatus().name(), resume.getId(), null));
            } catch (BusinessException e) {
                results.add(new BatchUploadResult(i, file.getOriginalFilename(), "FAILED", null, e.getMessage()));
            } catch (Exception e) {
                log.error("Unexpected error processing file: {}", file.getOriginalFilename(), e);
                results.add(new BatchUploadResult(i, file.getOriginalFilename(), "FAILED", null, "Internal server error"));
            }
        }

        return ResponseEntity.ok(ApiResponse.success(new BatchUploadResponse(results)));
    }

    @GetMapping
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('resume:read')")
    public ApiResponse<ResumeResponse> getById(@PathVariable UUID id) {
        Resume resume = resumeService.getById(id);
        return ApiResponse.success(ResumeResponse.from(resume));
    }

    @GetMapping("/{id}/download")
    @Transactional(readOnly = true)
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
    @Transactional
    @PreAuthorize("hasAuthority('resume:manage')")
    public ApiResponse<ResumeResponse> updateStructured(
            @PathVariable UUID id,
            @RequestBody UpdateStructuredRequest request) {
        Resume resume = resumeService.updateStructured(id, request);
        return ApiResponse.success(ResumeResponse.from(resume));
    }
}
