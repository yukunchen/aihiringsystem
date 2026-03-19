package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class AiMatchRequest {
    @JsonProperty("job_id")
    private UUID jobId;
    @JsonProperty("top_k")
    private int topK;
}
