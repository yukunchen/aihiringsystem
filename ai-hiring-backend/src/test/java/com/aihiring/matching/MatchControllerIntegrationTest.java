package com.aihiring.matching;

import com.aihiring.common.security.UserDetailsImpl;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnableWireMock({
    @ConfigureWireMock(name = "ai-matching", baseUrlProperties = "ai.matching.base-url")
})
class MatchControllerIntegrationTest {

    @InjectWireMock("ai-matching")
    WireMockServer wireMock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.aihiring.resume.ResumeRepository resumeRepository;

    @Autowired
    private com.aihiring.user.UserRepository userRepository;

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminUser() {
        UserDetailsImpl user = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "admin", "password", true, null,
            List.of("SUPER_ADMIN"),
            List.of("job:read", "job:manage", "resume:read")
        );
        return SecurityMockMvcRequestPostProcessors.user(user);
    }

    @Test
    void match_withValidJob_returnsResults() throws Exception {
        // Create a real resume in the database so it passes the existence filter
        var admin = userRepository.findByUsername("admin").orElseThrow();
        var resume = new com.aihiring.resume.Resume();
        resume.setFileName("test-candidate.pdf");
        resume.setCandidateName("Test Candidate");
        resume.setFilePath("/tmp/test.pdf");
        resume.setFileSize(1000L);
        resume.setFileType("PDF");
        resume.setUploadedBy(admin);
        resume = resumeRepository.save(resume);
        UUID resumeId = resume.getId();

        UUID jobId = UUID.randomUUID();
        String aiResponseBody = """
            {
              "job_id": "%s",
              "results": [
                {
                  "resume_id": "%s",
                  "vector_score": 0.92,
                  "llm_score": 87,
                  "reasoning": "Strong match",
                  "highlights": ["Java", "Spring Boot"]
                }
              ]
            }
            """.formatted(jobId, resumeId);

        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/match"))
            .willReturn(WireMock.aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(aiResponseBody)));

        mockMvc.perform(post("/api/match")
                .with(adminUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\": \"%s\", \"topK\": 10}".formatted(jobId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.results[0].llmScore").value(87))
            .andExpect(jsonPath("$.data.results[0].resumeId").value(resumeId.toString()))
            .andExpect(jsonPath("$.data.results[0].candidateName").value("Test Candidate"));
    }

    @Test
    void match_whenAiServiceReturns404_propagates422() throws Exception {
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/match"))
            .willReturn(WireMock.aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"detail\": \"Job not found in vector store\"}")));

        mockMvc.perform(post("/api/match")
                .with(adminUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\": \"%s\", \"topK\": 5}".formatted(UUID.randomUUID())))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void match_withoutPermission_returns403() throws Exception {
        UserDetailsImpl viewer = UserDetailsImpl.create(
            UUID.fromString("04000000-0000-0000-0000-000000000001"),
            "viewer", "password", true, null,
            List.of("USER"),
            List.of("resume:read")  // missing job:read
        );

        mockMvc.perform(post("/api/match")
                .with(SecurityMockMvcRequestPostProcessors.user(viewer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"jobId\": \"%s\", \"topK\": 5}".formatted(UUID.randomUUID())))
            .andExpect(status().isForbidden());
    }
}
