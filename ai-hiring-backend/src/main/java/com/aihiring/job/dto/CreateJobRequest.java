package com.aihiring.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateJobRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Description is required")
    private String description;

    private String requirements;
    private String skills;
    private String education;
    private String experience;
    private String salaryRange;
    private String location;

    @NotNull(message = "Department ID is required")
    private UUID departmentId;
}
