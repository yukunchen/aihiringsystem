package com.aihiring.job.dto;

import com.aihiring.job.JobDescription;
import com.aihiring.job.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class JobResponse {
    private UUID id;
    private String title;
    private String description;
    private String requirements;
    private String skills;
    private String education;
    private String experience;
    private String salaryRange;
    private String location;
    private JobStatus status;
    private DepartmentInfo department;
    private CreatedByInfo createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @AllArgsConstructor
    public static class DepartmentInfo {
        private UUID id;
        private String name;
    }

    @Getter
    @AllArgsConstructor
    public static class CreatedByInfo {
        private UUID id;
        private String username;
    }

    public static JobResponse from(JobDescription job) {
        return new JobResponse(
            job.getId(),
            job.getTitle(),
            job.getDescription(),
            job.getRequirements(),
            job.getSkills(),
            job.getEducation(),
            job.getExperience(),
            job.getSalaryRange(),
            job.getLocation(),
            job.getStatus(),
            new DepartmentInfo(
                job.getDepartment().getId(),
                job.getDepartment().getName()
            ),
            new CreatedByInfo(
                job.getCreatedBy().getId(),
                job.getCreatedBy().getUsername()
            ),
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }
}
