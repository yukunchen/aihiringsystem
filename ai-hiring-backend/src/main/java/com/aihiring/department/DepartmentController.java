package com.aihiring.department;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.department.dto.CreateDepartmentRequest;
import com.aihiring.department.dto.UpdateDepartmentRequest;
import com.aihiring.department.dto.DepartmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('department:manage')")
    public ApiResponse<Department> createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        return ApiResponse.success(departmentService.createDepartment(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<List<DepartmentResponse>> getDepartments() {
        return ApiResponse.success(departmentService.getDepartmentTree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:read')")
    public ApiResponse<Department> getDepartment(@PathVariable UUID id) {
        return ApiResponse.success(departmentService.getDepartmentById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public ApiResponse<Department> updateDepartment(@PathVariable UUID id, @Valid @RequestBody UpdateDepartmentRequest request) {
        return ApiResponse.success(departmentService.updateDepartment(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:manage')")
    public ApiResponse<Void> deleteDepartment(@PathVariable UUID id) {
        departmentService.deleteDepartment(id);
        return ApiResponse.success(null);
    }
}
