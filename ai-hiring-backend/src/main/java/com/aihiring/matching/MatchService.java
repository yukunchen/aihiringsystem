package com.aihiring.matching;

import com.aihiring.matching.dto.AiMatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MatchService {

    private final AiMatchingClient client;

    public AiMatchResponse match(UUID jobId, int topK) {
        return client.match(jobId, topK);
    }
}
