package com.aihiring.matching;

import com.aihiring.matching.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock AiMatchingClient client;
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
}
