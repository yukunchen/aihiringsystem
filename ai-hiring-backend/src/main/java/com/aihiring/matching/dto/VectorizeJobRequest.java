package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VectorizeJobRequest {
    @JsonProperty("job_id")
    private UUID jobId;
    private String title;
    private String description;
    private String requirements;
    private String skills;
}
