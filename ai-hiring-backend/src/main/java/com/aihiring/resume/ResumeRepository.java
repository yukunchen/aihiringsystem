package com.aihiring.resume;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {
    Page<Resume> findBySource(ResumeSource source, Pageable pageable);
    Page<Resume> findByStatus(ResumeStatus status, Pageable pageable);
    Page<Resume> findBySourceAndStatus(ResumeSource source, ResumeStatus status, Pageable pageable);
    Optional<Resume> findFirstByFileHash(String fileHash);
}
