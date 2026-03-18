package com.aihiring.job;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.common.security.UserDetailsImpl;
import com.aihiring.job.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @PostMapping
    @PreAuthorize("hasAuthority('job:manage')")
    public ApiResponse<JobResponse> create(
            @Valid @RequestBody CreateJobRequest request,
            @AuthenticationPrincipal UserDetailsImpl currentUser) {
        JobDescription job = jobService.create(request, currentUser.getId());
        return ApiResponse.success(JobResponse.from(job));
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('job:read')")
    public ApiResponse<Page<JobListResponse>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) JobStatus status,
            @RequestParam(value = "departmentId", required = false) UUID departmentId,
            Pageable pageable) {
        Page<JobDescription> page = jobService.list(keyword, status, departmentId, pageable);
        return ApiResponse.success(page.map(JobListResponse::from));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('job:read')")
    public ApiResponse<JobResponse> getById(@PathVariable UUID id) {
        JobDescription job = jobService.getById(id);
        return ApiResponse.success(JobResponse.from(job));
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAuthority('job:manage')")
    public ApiResponse<JobResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJobRequest request) {
        JobDescription job = jobService.update(id, request);
        return ApiResponse.success(JobResponse.from(job));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('job:manage')")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        jobService.delete(id);
        return ApiResponse.success(null);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('job:manage')")
    public ApiResponse<JobResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeStatusRequest request) {
        JobDescription job = jobService.changeStatus(id, request);
        return ApiResponse.success(JobResponse.from(job));
    }
}
