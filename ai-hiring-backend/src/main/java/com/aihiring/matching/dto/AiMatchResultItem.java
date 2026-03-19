package com.aihiring.matching.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter
@NoArgsConstructor
public class AiMatchResultItem {
    @JsonProperty("resume_id")
    private String resumeId;
    @JsonProperty("vector_score")
    private double vectorScore;
    @JsonProperty("llm_score")
    private int llmScore;
    private String reasoning;
    private List<String> highlights;
}
