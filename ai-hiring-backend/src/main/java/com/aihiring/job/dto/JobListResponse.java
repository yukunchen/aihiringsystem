package com.aihiring.job.dto;

import com.aihiring.job.JobDescription;
import com.aihiring.job.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class JobListResponse {
    private UUID id;
    private String title;
    private String descriptionPreview;
    private String skills;
    private String education;
    private String experience;
    private String salaryRange;
    private String location;
    private JobStatus status;
    private UUID departmentId;
    private String departmentName;
    private LocalDateTime createdAt;

    private static final int DESCRIPTION_PREVIEW_LENGTH = 200;

    public static JobListResponse from(JobDescription job) {
        String preview = job.getDescription().length() <= DESCRIPTION_PREVIEW_LENGTH
            ? job.getDescription()
            : job.getDescription().substring(0, DESCRIPTION_PREVIEW_LENGTH);

        return new JobListResponse(
            job.getId(),
            job.getTitle(),
            preview,
            job.getSkills(),
            job.getEducation(),
            job.getExperience(),
            job.getSalaryRange(),
            job.getLocation(),
            job.getStatus(),
            job.getDepartment().getId(),
            job.getDepartment().getName(),
            job.getCreatedAt()
        );
    }
}
