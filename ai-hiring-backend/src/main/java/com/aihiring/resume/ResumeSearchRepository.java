package com.aihiring.resume;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ResumeSearchRepository {
    Page<Resume> search(String query, Pageable pageable);
}
