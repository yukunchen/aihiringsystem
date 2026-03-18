package com.aihiring.resume;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("!test")
public class PostgresResumeSearchRepository implements ResumeSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Page<Resume> search(String query, Pageable pageable) {
        String sql = "SELECT * FROM resumes WHERE search_vector @@ plainto_tsquery('simple', :query) ORDER BY created_at DESC";
        Query nativeQuery = entityManager.createNativeQuery(sql, Resume.class);
        nativeQuery.setParameter("query", query);
        nativeQuery.setFirstResult((int) pageable.getOffset());
        nativeQuery.setMaxResults(pageable.getPageSize());
        List<Resume> results = nativeQuery.getResultList();

        String countSql = "SELECT COUNT(*) FROM resumes WHERE search_vector @@ plainto_tsquery('simple', :query)";
        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("query", query);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(results, pageable, total);
    }
}
