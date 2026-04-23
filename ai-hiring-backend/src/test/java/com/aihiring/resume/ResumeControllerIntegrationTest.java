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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

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
    void upload_sameFileTwice_secondShouldReturn409() throws Exception {
        byte[] bytes = ("Duplicate Test " + UUID.randomUUID() + "\nContent").getBytes();
        MockMultipartFile first = new MockMultipartFile("file", "dup-a.txt", "text/plain", bytes);
        MockMultipartFile second = new MockMultipartFile("file", "dup-b.txt", "text/plain", bytes);

        mockMvc.perform(multipart("/api/resumes/upload").file(first).with(adminUser()))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/resumes/upload").file(second).with(adminUser()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void upload_batchWithDuplicate_shouldMarkAsDuplicate() throws Exception {
        byte[] bytes = ("Batch Dup " + UUID.randomUUID() + "\nContent").getBytes();
        MockMultipartFile seed = new MockMultipartFile("files", "seed.txt", "text/plain", bytes);
        MockMultipartFile again = new MockMultipartFile("files", "again.txt", "text/plain", bytes);
        MockMultipartFile fresh = new MockMultipartFile("files", "fresh.txt", "text/plain",
            ("Fresh " + UUID.randomUUID()).getBytes());

        // seed first
        mockMvc.perform(multipart("/api/resumes/upload").file(seed).with(adminUser()))
                .andExpect(status().isOk());

        // batch with one duplicate and one new
        mockMvc.perform(multipart("/api/resumes/upload")
                .file(again)
                .file(fresh)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.duplicated").value(1))
                .andExpect(jsonPath("$.data.results[?(@.fileName=='again.txt')].status").value("DUPLICATE"));
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

    @Test
    void upload_withMultipleFiles_shouldReturnBatchResponse() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("files", "resume1.txt", "text/plain", "John Smith\nEngineer".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("files", "resume2.txt", "text/plain", "Jane Doe\nManager".getBytes());

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file1)
                .file(file2)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.succeeded").value(2))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.results[0].fileName").value("resume1.txt"))
                .andExpect(jsonPath("$.data.results[0].status").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.results[1].fileName").value("resume2.txt"))
                .andExpect(jsonPath("$.data.results[1].status").value("TEXT_EXTRACTED"));
    }

    @Test
    void upload_withNoFilesParam_shouldReturn400() throws Exception {
        // No 'file' and no 'files' param — Controller should return 400
        mockMvc.perform(multipart("/api/resumes/upload")
                .with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_withSingleFileUsingFilesParam_shouldWork() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "single.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(file)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void upload_withExceeds100Files_shouldReturn400() throws Exception {
        // Create 101 files (exceeds 100 limit)
        List<MockMultipartFile> files = IntStream.range(0, 101)
                .mapToObj(i -> new MockMultipartFile("files", "resume" + i + ".txt", "text/plain", ("content" + i).getBytes()))
                .collect(Collectors.toList());

        MockMultipartHttpServletRequestBuilder builder = multipart("/api/resumes/upload");
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        mockMvc.perform(builder.with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_withTotalSizeExceeds200MB_shouldReturn400() throws Exception {
        // Create 201 files at 1MB each = 201MB (exceeds 200MB limit)
        List<MockMultipartFile> files = IntStream.range(0, 201)
                .mapToObj(i -> new MockMultipartFile("files", "resume" + i + ".txt", "text/plain", new byte[1024 * 1024]))
                .collect(Collectors.toList());

        MockMultipartHttpServletRequestBuilder builder = multipart("/api/resumes/upload");
        for (MockMultipartFile file : files) {
            builder.file(file);
        }
        mockMvc.perform(builder.with(adminUser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void upload_withPartialFailure_shouldReturn200WithFailedEntries() throws Exception {
        MockMultipartFile good1 = new MockMultipartFile("files", "good1.txt", "text/plain", "valid content".getBytes());
        MockMultipartFile bad = new MockMultipartFile("files", "bad.jpg", "image/jpeg", "not a resume".getBytes());
        MockMultipartFile good2 = new MockMultipartFile("files", "good2.txt", "text/plain", "also valid".getBytes());

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(good1)
                .file(bad)
                .file(good2)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.succeeded").value(2))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.results[0].fileName").value("good1.txt"))
                .andExpect(jsonPath("$.data.results[0].status").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.results[1].fileName").value("bad.jpg"))
                .andExpect(jsonPath("$.data.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[1].error").exists())
                .andExpect(jsonPath("$.data.results[2].fileName").value("good2.txt"))
                .andExpect(jsonPath("$.data.results[2].status").value("TEXT_EXTRACTED"));
    }

    @Test
    void upload_withOneFileOver10MBInBatch_shouldFailOnlyThatFile() throws Exception {
        byte[] normalContent = "normal resume".getBytes();
        byte[] bigContent = new byte[11 * 1024 * 1024]; // 11MB - exceeds limit
        MockMultipartFile good = new MockMultipartFile("files", "good.txt", "text/plain", normalContent);
        MockMultipartFile tooBig = new MockMultipartFile("files", "big.pdf", "application/pdf", bigContent);

        mockMvc.perform(multipart("/api/resumes/upload")
                .file(good)
                .file(tooBig)
                .with(adminUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.succeeded").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.results[0].status").value("TEXT_EXTRACTED"))
                .andExpect(jsonPath("$.data.results[1].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[1].error").exists());
    }
}
