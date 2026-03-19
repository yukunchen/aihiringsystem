package com.aihiring.matching.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Getter
@Setter
public class MatchRequest {
    @NotNull
    private UUID jobId;
    @Min(1) @Max(50)
    private int topK = 10;
}
