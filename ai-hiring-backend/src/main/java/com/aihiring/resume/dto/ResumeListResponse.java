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
public class ResumeListResponse {
    private UUID id;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String rawTextPreview;
    private ResumeSource source;
    private ResumeStatus status;
    private ResumeResponse.UploadedByInfo uploadedBy;
    private String candidateName;
    private String candidateEmail;
    private LocalDateTime createdAt;

    private static final int RAW_TEXT_PREVIEW_LENGTH = 200;

    public static ResumeListResponse from(Resume resume) {
        String preview = resume.getRawText() == null ? null :
            resume.getRawText().substring(0, Math.min(RAW_TEXT_PREVIEW_LENGTH, resume.getRawText().length()));

        return new ResumeListResponse(
            resume.getId(),
            resume.getFileName(),
            resume.getFileSize(),
            resume.getFileType(),
            preview,
            resume.getSource(),
            resume.getStatus(),
            new ResumeResponse.UploadedByInfo(
                resume.getUploadedBy().getId(),
                resume.getUploadedBy().getUsername()
            ),
            resume.getCandidateName(),
            resume.getCandidateEmail(),
            resume.getCreatedAt()
        );
    }
}
