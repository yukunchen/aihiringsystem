package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class AiMatchResponse {
    @JsonProperty("job_id")
    private String jobId;
    private List<AiMatchResultItem> results;
}
