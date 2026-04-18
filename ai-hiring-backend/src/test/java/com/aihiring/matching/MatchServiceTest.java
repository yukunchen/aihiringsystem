package com.aihiring.matching;

import com.aihiring.job.JobDescription;
import com.aihiring.job.JobRepository;
import com.aihiring.matching.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock AiMatchingClient client;
    @Mock JobRepository jobRepository;
    @InjectMocks MatchService matchService;

    @Test
    void match_delegatesToClientAndReturnsResponse() throws Exception {
        UUID jobId = UUID.randomUUID();
        String json = """
            {"job_id": "%s", "results": [
              {"resume_id": "r1", "vector_score": 0.9, "llm_score": 80,
               "reasoning": "Good match", "highlights": ["Java"]}
            ]}""".formatted(jobId);
        AiMatchResponse aiResponse = new ObjectMapper().readValue(json, AiMatchResponse.class);
        when(client.match(eq(jobId), eq(10))).thenReturn(aiResponse);

        AiMatchResponse result = matchService.match(jobId, 10);

        assertThat(result).isSameAs(aiResponse);
        assertThat(result.getResults()).hasSize(1);
        verify(client).match(jobId, 10);
    }

    @Test
    void match_whenJobNotIndexed_vectorizesOnDemandAndRetries() throws Exception {
        UUID jobId = UUID.randomUUID();
        JobDescription job = new JobDescription();
        job.setId(jobId);
        job.setTitle("Backend Engineer");
        job.setDescription("desc");
        job.setRequirements("req");
        job.setSkills("Java, Spring");

        String json = """
            {"job_id": "%s", "results": []}""".formatted(jobId);
        AiMatchResponse aiResponse = new ObjectMapper().readValue(json, AiMatchResponse.class);

        HttpClientErrorException.NotFound notFound = (HttpClientErrorException.NotFound)
            HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);

        when(client.match(jobId, 10))
            .thenThrow(notFound)
            .thenReturn(aiResponse);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(client.vectorizeJob(jobId, "Backend Engineer", "desc", "req", "Java, Spring"))
            .thenReturn(true);

        AiMatchResponse result = matchService.match(jobId, 10);

        assertThat(result).isSameAs(aiResponse);
        verify(client, times(2)).match(jobId, 10);
        verify(client).vectorizeJob(jobId, "Backend Engineer", "desc", "req", "Java, Spring");
    }

    @Test
    void match_whenVectorizationFails_rethrowsNotFound() {
        UUID jobId = UUID.randomUUID();
        JobDescription job = new JobDescription();
        job.setId(jobId);
        job.setTitle("t");

        HttpClientErrorException.NotFound notFound = (HttpClientErrorException.NotFound)
            HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null);

        when(client.match(jobId, 5)).thenThrow(notFound);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(client.vectorizeJob(any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> matchService.match(jobId, 5))
            .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
