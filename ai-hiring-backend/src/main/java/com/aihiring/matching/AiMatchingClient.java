package com.aihiring.matching;

import com.aihiring.matching.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

@Slf4j
@Component
public class AiMatchingClient {

    private final RestClient restClient;

    public AiMatchingClient(
        RestClient.Builder builder,
        @Value("${ai.matching.base-url:http://localhost:8001}") String baseUrl
    ) {
        this.restClient = builder
            .baseUrl(baseUrl)
            .requestFactory(new SimpleClientHttpRequestFactory())
            .build();
    }

    public boolean vectorizeResume(UUID resumeId, String rawText) {
        try {
            restClient.post()
                .uri("/internal/vectorize/resume")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VectorizeResumeRequest(resumeId, rawText))
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to vectorize resume {}: {}", resumeId, e.getMessage());
            return false;
        }
    }

    public boolean deleteResumeVector(UUID resumeId) {
        try {
            restClient.delete()
                .uri("/internal/vectorize/resume/{id}", resumeId)
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to delete resume vector {}: {}", resumeId, e.getMessage());
            return false;
        }
    }

    public boolean vectorizeJob(UUID jobId, String title, String description,
                              String requirements, String skills) {
        try {
            restClient.post()
                .uri("/internal/vectorize/job")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new VectorizeJobRequest(jobId, title, description, requirements, skills))
                .retrieve()
                .toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to vectorize job {}: {}", jobId, e.getMessage());
            return false;
        }
    }

    public AiMatchResponse match(UUID jobId, int topK) {
        return restClient.post()
            .uri("/match")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new AiMatchRequest(jobId, topK))
            .retrieve()
            .body(AiMatchResponse.class);
    }
}
