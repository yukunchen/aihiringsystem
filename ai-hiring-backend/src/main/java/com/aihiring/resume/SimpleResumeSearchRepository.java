package com.aihiring.resume;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Profile("test")
public class SimpleResumeSearchRepository implements ResumeSearchRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Page<Resume> search(String query, Pageable pageable) {
        String jpql = "SELECT r FROM Resume r WHERE LOWER(r.rawText) LIKE LOWER(:query) ORDER BY r.createdAt DESC";
        TypedQuery<Resume> typedQuery = entityManager.createQuery(jpql, Resume.class);
        typedQuery.setParameter("query", "%" + query + "%");
        typedQuery.setFirstResult((int) pageable.getOffset());
        typedQuery.setMaxResults(pageable.getPageSize());
        List<Resume> results = typedQuery.getResultList();

        String countJpql = "SELECT COUNT(r) FROM Resume r WHERE LOWER(r.rawText) LIKE LOWER(:query)";
        TypedQuery<Long> countQuery = entityManager.createQuery(countJpql, Long.class);
        countQuery.setParameter("query", "%" + query + "%");
        long total = countQuery.getSingleResult();

        return new PageImpl<>(results, pageable, total);
    }
}
