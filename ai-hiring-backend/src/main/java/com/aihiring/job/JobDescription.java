package com.aihiring.job;

import com.aihiring.common.entity.BaseEntity;
import com.aihiring.department.Department;
import com.aihiring.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "job_descriptions")
@Getter
@Setter
public class JobDescription extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(columnDefinition = "jsonb")
    private String skills;

    @Column(length = 50)
    private String education;

    @Column(length = 50)
    private String experience;

    @Column(name = "salary_range", length = 100)
    private String salaryRange;

    @Column(length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;
}
