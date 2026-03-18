package com.aihiring.job.dto;

import com.aihiring.job.JobStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangeStatusRequest {

    @NotNull(message = "Status is required")
    private JobStatus status;
}
