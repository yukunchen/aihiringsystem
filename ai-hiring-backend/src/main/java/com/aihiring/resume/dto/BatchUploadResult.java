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
