package com.aihiring.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<JobDescription, UUID> {

    Page<JobDescription> findByStatus(JobStatus status, Pageable pageable);

    Page<JobDescription> findByDepartmentId(UUID departmentId, Pageable pageable);

    Page<JobDescription> findByStatusAndDepartmentId(JobStatus status, UUID departmentId, Pageable pageable);

    @Query("SELECT j FROM JobDescription j WHERE LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<JobDescription> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
