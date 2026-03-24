package com.aihiring.resume.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

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
