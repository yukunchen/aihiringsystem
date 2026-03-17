package com.aihiring.role;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.role.dto.CreateRoleRequest;
import com.aihiring.role.dto.UpdateRoleRequest;
import com.aihiring.role.dto.RoleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional
    public Role createRole(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new BusinessException(409, "Role already exists");
        }

        Role role = new Role();
        role.setName(request.getName());
        role.setDescription(request.getDescription());

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
            role.getPermissions().addAll(permissions);
        }

        return roleRepository.save(role);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

    @Transactional
    public Role updateRole(UUID id, UpdateRoleRequest request) {
        Role role = getRoleById(id);

        if (request.getName() != null && !request.getName().equals(role.getName())) {
            if (roleRepository.existsByName(request.getName())) {
                throw new BusinessException(409, "Role name already exists");
            }
            role.setName(request.getName());
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getPermissionIds() != null) {
            List<Permission> permissions = permissionRepository.findAllById(request.getPermissionIds());
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
        }

        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = getRoleById(id);
        // Check if role is assigned to users
        if (role.getUsers() != null && !role.getUsers().isEmpty()) {
            throw new BusinessException(409, "Cannot delete role assigned to users");
        }
        roleRepository.delete(role);
    }
}
