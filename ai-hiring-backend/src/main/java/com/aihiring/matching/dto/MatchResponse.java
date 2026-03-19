package com.aihiring.matching.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class MatchResponse {
    private UUID jobId;
    private List<MatchResultItem> results;
}
