package com.aihiring.role;

import com.aihiring.common.dto.ApiResponse;
import com.aihiring.role.dto.CreateRoleRequest;
import com.aihiring.role.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<Role> createRole(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.success(roleService.createRole(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    public ApiResponse<List<Role>> getRoles() {
        return ApiResponse.success(roleService.getAllRoles());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<Role> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.success(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<Void> deleteRole(@PathVariable UUID id) {
        roleService.deleteRole(id);
        return ApiResponse.success(null);
    }
}
