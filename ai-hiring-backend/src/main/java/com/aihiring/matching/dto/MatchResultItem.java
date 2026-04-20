package com.aihiring.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class MatchResultItem {
    private String resumeId;
    private String candidateName;
    private double vectorScore;
    private int llmScore;
    private String reasoning;
    private List<String> highlights;
}
