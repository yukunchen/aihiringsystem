package com.aihiring.matching;

import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.job.JobDescription;
import com.aihiring.job.JobRepository;
import com.aihiring.matching.dto.AiMatchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final AiMatchingClient client;
    private final JobRepository jobRepository;

    public AiMatchResponse match(UUID jobId, int topK) {
        try {
            return client.match(jobId, topK);
        } catch (HttpClientErrorException.NotFound e) {
            log.info("Job {} not indexed, vectorizing on demand", jobId);
            JobDescription job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job description not found"));
            boolean vectorized = client.vectorizeJob(
                job.getId(), job.getTitle(), job.getDescription(),
                job.getRequirements(), job.getSkills()
            );
            if (!vectorized) {
                throw e;
            }
            return client.match(jobId, topK);
        }
    }
}
