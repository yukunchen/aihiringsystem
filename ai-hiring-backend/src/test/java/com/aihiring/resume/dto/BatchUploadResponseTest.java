package com.aihiring.resume.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

    @Test
    void resultsWithNullElement_shouldNotThrow() {
        List<BatchUploadResult> results = new ArrayList<>();
        results.add(new BatchUploadResult(0, "a.pdf", "UPLOADED", UUID.randomUUID(), null));
        results.add(null);
        results.add(new BatchUploadResult(2, "c.pdf", "UPLOADED", UUID.randomUUID(), null));
        // Should not throw NPE, counts should exclude null
        BatchUploadResponse response = new BatchUploadResponse(results);
        assertEquals(3, response.getTotal());
        assertEquals(2, response.getSucceeded());
        assertEquals(0, response.getFailed());
    }
}