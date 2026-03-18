package com.aihiring.job;

import com.aihiring.common.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String ADMIN_USER_ID = "04000000-0000-0000-0000-000000000001";
    private static final String ENGINEERING_DEPT_ID = "03000000-0000-0000-0000-000000000002";

    private RequestPostProcessor adminUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString(ADMIN_USER_ID),
            "admin", "password", true, null,
            List.of("SUPER_ADMIN"),
            List.of("job:read", "job:manage")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    private RequestPostProcessor readOnlyUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString(ADMIN_USER_ID),
            "viewer", "password", true, null,
            List.of("USER"),
            List.of("job:read")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    private String createJobJson() {
        return """
            {
                "title": "Senior Java Developer",
                "description": "We are looking for a senior Java developer with Spring Boot experience.",
                "requirements": "5+ years Java experience",
                "skills": "[\\"Java\\", \\"Spring Boot\\"]",
                "education": "本科",
                "experience": "3-5年",
                "salaryRange": "15k-25k",
                "location": "北京",
                "departmentId": "%s"
            }
            """.formatted(ENGINEERING_DEPT_ID);
    }

    private String createJobAndGetId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJobJson())
                .with(adminUser()))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.split("\"id\":\"")[1].split("\"")[0];
    }

    @Test
    void create_withValidRequest_shouldReturn200WithDraftStatus() throws Exception {
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJobJson())
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.department.name").value("Engineering"));
    }

    @Test
    void create_withMissingTitle_shouldReturn400() throws Exception {
        String body = """
            {
                "description": "We are looking for a developer.",
                "departmentId": "%s"
            }
            """.formatted(ENGINEERING_DEPT_ID);

        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void create_withoutManagePermission_shouldReturn403() throws Exception {
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJobJson())
                .with(readOnlyUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_shouldReturnPaginatedResults() throws Exception {
        // Create a job first to ensure there's data
        createJobAndGetId();

        mockMvc.perform(get("/api/jobs")
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getById_withValidId_shouldReturn200WithTitle() throws Exception {
        String id = createJobAndGetId();

        mockMvc.perform(get("/api/jobs/" + id)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Senior Java Developer"));
    }

    @Test
    void getById_withInvalidId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/jobs/" + UUID.randomUUID())
                .with(adminUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void update_shouldReturnModifiedFields() throws Exception {
        String id = createJobAndGetId();

        String updateBody = """
            {
                "title": "Principal Java Developer",
                "location": "上海"
            }
            """;

        mockMvc.perform(put("/api/jobs/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("Principal Java Developer"))
                .andExpect(jsonPath("$.data.location").value("上海"));
    }

    @Test
    void changeStatus_draftToPublished_shouldReturn200WithPublishedStatus() throws Exception {
        String id = createJobAndGetId();

        String statusBody = """
            {"status": "PUBLISHED"}
            """;

        mockMvc.perform(put("/api/jobs/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void changeStatus_invalidTransition_shouldReturn400() throws Exception {
        String id = createJobAndGetId();

        // DRAFT -> CLOSED is not allowed
        String statusBody = """
            {"status": "CLOSED"}
            """;

        mockMvc.perform(put("/api/jobs/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody)
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void delete_draftJob_shouldReturn200ThenGetReturns404() throws Exception {
        String id = createJobAndGetId();

        mockMvc.perform(delete("/api/jobs/" + id)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Verify it's gone
        mockMvc.perform(get("/api/jobs/" + id)
                .with(adminUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_publishedJob_shouldReturn400() throws Exception {
        String id = createJobAndGetId();

        // Publish first
        mockMvc.perform(put("/api/jobs/" + id + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"PUBLISHED\"}")
                .with(adminUser()));

        // Attempt delete
        mockMvc.perform(delete("/api/jobs/" + id)
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unauthenticated_shouldReturn403() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isForbidden());
    }
}
