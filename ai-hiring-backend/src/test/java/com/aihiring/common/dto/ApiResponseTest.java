package com.aihiring.common.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {
    @Test
    void success_shouldReturnCorrectFormat() {
        ApiResponse<String> response = ApiResponse.success("testData");
        assertEquals(200, response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("testData", response.getData());
    }

    @Test
    void error_shouldReturnCorrectFormat() {
        ApiResponse<Void> response = ApiResponse.error(401, "Unauthorized");
        assertEquals(401, response.getCode());
        assertEquals("Unauthorized", response.getMessage());
        assertNull(response.getData());
    }
}
