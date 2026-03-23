package com.aihiring.resume;

import com.aihiring.common.entity.BaseEntity;
import com.aihiring.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "resumes")
@Getter
@Setter
public class Resume extends BaseEntity {

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_type", nullable = false, length = 100)
    private String fileType;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResumeSource source = ResumeSource.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResumeStatus status = ResumeStatus.UPLOADED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "candidate_name", length = 100)
    private String candidateName;

    @Column(name = "candidate_phone", length = 50)
    private String candidatePhone;

    @Column(name = "candidate_email", length = 100)
    private String candidateEmail;

    @Column(columnDefinition = "TEXT")
    private String education;

    @Column(columnDefinition = "TEXT")
    private String experience;

    @Column(columnDefinition = "TEXT")
    private String projects;

    @Column(columnDefinition = "jsonb")
    private String skills;
}
