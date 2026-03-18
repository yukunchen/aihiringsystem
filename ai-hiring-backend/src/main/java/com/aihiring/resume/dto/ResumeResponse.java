package com.aihiring.resume.dto;

import com.aihiring.resume.Resume;
import com.aihiring.resume.ResumeSource;
import com.aihiring.resume.ResumeStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ResumeResponse {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String rawText;
    private ResumeSource source;
    private ResumeStatus status;
    private UploadedByInfo uploadedBy;
    private String candidateName;
    private String candidatePhone;
    private String candidateEmail;
    private String education;
    private String experience;
    private String projects;
    private String skills;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter
    @AllArgsConstructor
    public static class UploadedByInfo {
        private UUID id;
        private String username;
    }

    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
            resume.getId(),
            resume.getFileName(),
            resume.getFileSize(),
            resume.getFileType(),
            resume.getRawText(),
            resume.getSource(),
            resume.getStatus(),
            new UploadedByInfo(
                resume.getUploadedBy().getId(),
                resume.getUploadedBy().getUsername()
            ),
            resume.getCandidateName(),
            resume.getCandidatePhone(),
            resume.getCandidateEmail(),
            resume.getEducation(),
            resume.getExperience(),
            resume.getProjects(),
            resume.getSkills(),
            resume.getCreatedAt(),
            resume.getUpdatedAt()
        );
    }
}
