package com.aihiring.resume.dto;

import lombok.Getter;

import java.util.List;

@Getter
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