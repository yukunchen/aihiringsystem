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
import org.springframework.transaction.annotation.Transactional;
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
