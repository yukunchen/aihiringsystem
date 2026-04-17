package com.aihiring.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test that exercises the full JWT auth flow:
 * login -> get token -> call /api/auth/me -> verify roles and permissions are populated.
 *
 * This catches issues where JwtAuthFilter fails to load roles/permissions from the DB
 * (e.g., due to Hibernate lazy-loading issues with open-in-view=false).
 *
 * Regression test for issue #103.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthMeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void me_afterLogin_shouldReturnRolesAndPermissions() throws Exception {
        // Step 1: Login to get a real JWT token
        String loginBody = """
            {"username": "admin", "password": "admin123"}
            """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn();

        // Extract access token
        String responseBody = loginResult.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        String accessToken = (String) data.get("accessToken");

        // Step 2: Call /api/auth/me with the JWT token
        // This exercises the full JwtAuthFilter -> findByIdWithRolesAndPermissions -> UserDetailsImpl path
        mockMvc.perform(get("/api/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"))
            .andExpect(jsonPath("$.data.roles").isArray())
            .andExpect(jsonPath("$.data.roles", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.data.roles", hasItem("SUPER_ADMIN")))
            .andExpect(jsonPath("$.data.permissions").isArray())
            .andExpect(jsonPath("$.data.permissions", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$.data.permissions", hasItem("job:read")));
    }

    @Test
    void matchEndpoint_afterLogin_adminShouldNotGet403() throws Exception {
        // Login as admin
        String loginBody = """
            {"username": "admin", "password": "admin123"}
            """;

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        String accessToken = (String) data.get("accessToken");

        // Call /api/match - should NOT get 403
        // (it may get a different error since there's no AI matching service running,
        //  but 403 specifically means the permissions were not loaded)
        mockMvc.perform(post("/api/match")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\": \"00000000-0000-0000-0000-000000000001\", \"topK\": 5}"))
            .andExpect(status().is(not(403)));
    }
}
