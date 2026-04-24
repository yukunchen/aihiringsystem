package com.aihiring.matching;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class AiMatchingClientTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    AiMatchingClient client;

    @BeforeEach
    void setUp() {
        client = new AiMatchingClient(
            RestClient.builder(),
            "http://localhost:" + wm.getPort()
        );
    }

    @Test
    void vectorizeResume_success_returnsTrue() {
        UUID resumeId = UUID.randomUUID();
        wm.stubFor(post(urlEqualTo("/internal/vectorize/resume"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\"}")));

        boolean result = client.vectorizeResume(resumeId, "John Smith Java developer");

        assertThat(result).isTrue();
        wm.verify(postRequestedFor(urlEqualTo("/internal/vectorize/resume"))
            .withRequestBody(matchingJsonPath("$.resume_id"))
            .withRequestBody(matchingJsonPath("$.raw_text", equalTo("John Smith Java developer"))));
    }

    @Test
    void vectorizeResume_serviceError_returnsFalse() {
        wm.stubFor(post(urlEqualTo("/internal/vectorize/resume"))
            .willReturn(aResponse().withStatus(500)));

        boolean result = client.vectorizeResume(UUID.randomUUID(), "text");

        assertThat(result).isFalse();
    }

    @Test
    void vectorizeJob_success_returnsTrue() {
        UUID jobId = UUID.randomUUID();
        wm.stubFor(post(urlEqualTo("/internal/vectorize/job"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\"}")));

        boolean result = client.vectorizeJob(jobId, "Engineer", "Build things", "5yr", "Java");

        assertThat(result).isTrue();
        wm.verify(postRequestedFor(urlEqualTo("/internal/vectorize/job"))
            .withRequestBody(matchingJsonPath("$.job_id"))
            .withRequestBody(matchingJsonPath("$.title", equalTo("Engineer"))));
    }

    @Test
    void vectorizeJob_serviceError_returnsFalse() {
        wm.stubFor(post(urlEqualTo("/internal/vectorize/job"))
            .willReturn(aResponse().withStatus(503)));

        boolean result = client.vectorizeJob(UUID.randomUUID(), "T", "D", null, null);

        assertThat(result).isFalse();
    }

    @Test
    void deleteResumeVector_success_returnsTrue() {
        UUID resumeId = UUID.randomUUID();
        wm.stubFor(delete(urlEqualTo("/internal/vectorize/resume/" + resumeId))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"ok\"}")));

        boolean result = client.deleteResumeVector(resumeId);

        assertThat(result).isTrue();
        wm.verify(deleteRequestedFor(urlEqualTo("/internal/vectorize/resume/" + resumeId)));
    }

    @Test
    void deleteResumeVector_serviceError_returnsFalse() {
        UUID resumeId = UUID.randomUUID();
        wm.stubFor(delete(urlEqualTo("/internal/vectorize/resume/" + resumeId))
            .willReturn(aResponse().withStatus(500)));

        boolean result = client.deleteResumeVector(resumeId);

        assertThat(result).isFalse();
    }

    @Test
    void match_returnsDeserializedResponse() {
        UUID jobId = UUID.randomUUID();
        wm.stubFor(post(urlEqualTo("/match"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {"job_id":"%s","results":[
                      {"resume_id":"r1","vector_score":0.9,"llm_score":85,
                       "reasoning":"Good","highlights":["Java"]}
                    ]}""".formatted(jobId))));

        var response = client.match(jobId, 5);

        assertThat(response.getJobId()).isEqualTo(jobId.toString());
        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getLlmScore()).isEqualTo(85);
    }
}
