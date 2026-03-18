package com.aihiring.job.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateJobRequest {

    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;
    private String requirements;
    private String skills;
    private String education;
    private String experience;
    private String salaryRange;
    private String location;
    private UUID departmentId;
}
