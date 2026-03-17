package com.aihiring.role;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.aihiring.role.dto.CreateRoleRequest;
import com.aihiring.common.exception.BusinessException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void createRole_withValidRequest_shouldCreate() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("HR_ADMIN");
        request.setDescription("HR Administrator");

        when(roleRepository.existsByName("HR_ADMIN")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> {
            Role r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        Role result = roleService.createRole(request);

        assertNotNull(result);
        assertEquals("HR_ADMIN", result.getName());
    }
}
