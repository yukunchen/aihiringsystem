package com.aihiring.test;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
@Profile({"dev", "staging"})
public class TestCleanupController {

    @Value("${test.secret:}")
    private String testSecret;

    @PostMapping("/cleanup")
    public ResponseEntity<?> cleanup(
            @RequestHeader("X-Test-Secret") String secret,
            @RequestParam(required = false, defaultValue = "false") boolean includeResumes,
            @RequestParam(required = false, defaultValue = "false") boolean includeJobs) {

        if (testSecret == null || testSecret.isEmpty() || !testSecret.equals(secret)) {
            return ResponseEntity.status(403).body("Invalid test secret");
        }

        // Clean up test data - records created by test user
        // This is a simplified implementation; extend as needed

        return ResponseEntity.ok("Cleanup completed");
    }
}
