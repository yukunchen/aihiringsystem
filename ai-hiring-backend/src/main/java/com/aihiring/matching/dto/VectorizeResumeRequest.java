package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VectorizeResumeRequest {
    @JsonProperty("resume_id")
    private UUID resumeId;
    @JsonProperty("raw_text")
    private String rawText;
}
