package com.aihiring.resume.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class BatchUploadResponse {
    private final int total;
    private final int succeeded;
    private final int failed;
    private final int duplicated;
    private final List<BatchUploadResult> results;

    public BatchUploadResponse(List<BatchUploadResult> results) {
        this.results = results;
        this.total = results.size();
        this.succeeded = (int) results.stream().filter(r -> r != null && ("UPLOADED".equals(r.getStatus()) || "TEXT_EXTRACTED".equals(r.getStatus()))).count();
        this.failed = (int) results.stream().filter(r -> r != null && "FAILED".equals(r.getStatus())).count();
        this.duplicated = (int) results.stream().filter(r -> r != null && "DUPLICATE".equals(r.getStatus())).count();
    }
}