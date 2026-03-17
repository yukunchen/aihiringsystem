package com.aihiring.department;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    List<Department> findByParentIsNull();

    @Query("SELECT d FROM Department d WHERE d.parent.id = :parentId")
    List<Department> findByParentId(UUID parentId);

    boolean existsByNameAndParentId(String name, UUID parentId);
}
