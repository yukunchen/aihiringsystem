package com.aihiring.department;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.department.dto.CreateDepartmentRequest;
import com.aihiring.department.dto.UpdateDepartmentRequest;
import com.aihiring.department.dto.DepartmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional
    public Department createDepartment(CreateDepartmentRequest request) {
        Department department = new Department();
        department.setName(request.getName());

        if (request.getParentId() != null) {
            Department parent = departmentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent department not found"));
            department.setParent(parent);
        }

        return departmentRepository.save(department);
    }

    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Department getDepartmentById(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
    }

    @Transactional
    public Department updateDepartment(UUID id, UpdateDepartmentRequest request) {
        Department department = getDepartmentById(id);

        if (request.getName() != null) {
            department.setName(request.getName());
        }

        if (request.getParentId() != null) {
            // Cycle detection
            if (request.getParentId().equals(id)) {
                throw new BusinessException(400, "Department cannot be its own parent");
            }

            // Check for circular reference
            UUID newParentId = request.getParentId();
            Department current = department;
            while (current.getParent() != null) {
                if (current.getParent().getId().equals(newParentId)) {
                    throw new BusinessException(400, "Cannot create circular department hierarchy");
                }
                current = current.getParent();
            }

            Department parent = departmentRepository.findById(newParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent department not found"));
            department.setParent(parent);
        }

        return departmentRepository.save(department);
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        Department department = getDepartmentById(id);

        // Check for children
        if (department.getChildren() != null && !department.getChildren().isEmpty()) {
            throw new BusinessException(409, "Cannot delete department with children");
        }

        // Check for users
        if (department.getUsers() != null && !department.getUsers().isEmpty()) {
            throw new BusinessException(409, "Cannot delete department with users");
        }

        departmentRepository.delete(department);
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> getDepartmentTree() {
        List<Department> roots = departmentRepository.findByParentIsNull();
        return roots.stream()
                .map(this::toTreeResponse)
                .collect(Collectors.toList());
    }

    private DepartmentResponse toTreeResponse(Department department) {
        DepartmentResponse response = new DepartmentResponse();
        response.setId(department.getId());
        response.setName(department.getName());
        response.setParentId(department.getParent() != null ? department.getParent().getId() : null);
        response.setCreatedAt(department.getCreatedAt());
        response.setUpdatedAt(department.getUpdatedAt());

        if (department.getChildren() != null && !department.getChildren().isEmpty()) {
            response.setChildren(department.getChildren().stream()
                    .map(this::toTreeResponse)
                    .collect(Collectors.toList()));
        }

        return response;
    }
}
