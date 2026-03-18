package com.aihiring.resume;

import com.aihiring.common.security.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResumeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private RequestPostProcessor adminUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "admin", "password", true, null,
            List.of("SUPER_ADMIN"),
            List.of("resume:read", "resume:manage", "user:read", "user:manage")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    private RequestPostProcessor readOnlyUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "viewer", "password", true, null,
            List.of("USER"),
            List.of("resume:read")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    @Test
    void upload_withValidFile_shouldReturn200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain", "John Smith\nSoftware Engineer\nJava Spring".getBytes()
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("resume.txt"))
                .andExpect(jsonPath("$.data.status").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.rawText").exists());
    }

    @Test
    void upload_withEmptyFile_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "empty.txt", "text/plain", new byte[0]
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_withUnsupportedType_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "image.jpg", "image/jpeg", "fake image".getBytes()
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_withoutPermission_shouldReturn403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.txt", "text/plain", "content".getBytes()
        );

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(readOnlyUser()))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_shouldReturnPaginatedResults() throws Exception {
        // Upload a file first
        MockMultipartFile file = new MockMultipartFile(
            "file", "list-test.txt", "text/plain", "Test content for listing".getBytes()
        );
        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()));

        mockMvc.perform(get("/api/resumes")
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void getById_withValidId_shouldReturnResume() throws Exception {
        // Upload a file first
        MockMultipartFile file = new MockMultipartFile(
            "file", "detail-test.txt", "text/plain", "Detail test content".getBytes()
        );
        MvcResult uploadResult = mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        String id = responseBody.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(get("/api/resumes/" + id)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value("detail-test.txt"));
    }

    @Test
    void getById_withInvalidId_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/resumes/" + UUID.randomUUID())
                .with(adminUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void delete_shouldRemoveResume() throws Exception {
        // Upload a file first
        MockMultipartFile file = new MockMultipartFile(
            "file", "delete-test.txt", "text/plain", "Delete test content".getBytes()
        );
        MvcResult uploadResult = mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        String id = responseBody.split("\"id\":\"")[1].split("\"")[0];

        mockMvc.perform(delete("/api/resumes/" + id)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // Verify it's gone
        mockMvc.perform(get("/api/resumes/" + id)
                .with(adminUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_shouldReturn401Or403() throws Exception {
        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isForbidden());
    }
}
