package com.aihiring;

import com.aihiring.department.Department;
import com.aihiring.department.DepartmentRepository;
import com.aihiring.role.Permission;
import com.aihiring.role.PermissionRepository;
import com.aihiring.role.Role;
import com.aihiring.role.RoleRepository;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private DataInitializer dataInitializer;

    private final List<Permission> savedPermissions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(userRepository.count()).thenReturn(0L);
        when(permissionRepository.save(any(Permission.class))).thenAnswer(inv -> {
            Permission p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            savedPermissions.add(p);
            return p;
        });
        when(permissionRepository.findAll()).thenAnswer(inv -> new ArrayList<>(savedPermissions));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> {
            Department d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(any())).thenReturn("$2b$10$hashedpassword");
    }

    @Test
    void run_shouldSeedAllTwelvePermissions() throws Exception {
        dataInitializer.run();

        Set<String> permissionNames = savedPermissions.stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());

        assertTrue(permissionNames.contains("user:read"), "Missing permission: user:read");
        assertTrue(permissionNames.contains("user:manage"), "Missing permission: user:manage");
        assertTrue(permissionNames.contains("department:read"), "Missing permission: department:read");
        assertTrue(permissionNames.contains("department:manage"), "Missing permission: department:manage");
        assertTrue(permissionNames.contains("role:read"), "Missing permission: role:read");
        assertTrue(permissionNames.contains("role:manage"), "Missing permission: role:manage");
        assertTrue(permissionNames.contains("resume:read"), "Missing permission: resume:read");
        assertTrue(permissionNames.contains("resume:manage"), "Missing permission: resume:manage");
        assertTrue(permissionNames.contains("job:read"), "Missing permission: job:read");
        assertTrue(permissionNames.contains("job:manage"), "Missing permission: job:manage");
        assertTrue(permissionNames.contains("match:read"), "Missing permission: match:read");
        assertTrue(permissionNames.contains("match:execute"), "Missing permission: match:execute");

        assertEquals(12, savedPermissions.size(), "Should seed exactly 12 permissions");
    }

    @Test
    void run_shouldSeedThreeDepartments() throws Exception {
        dataInitializer.run();

        ArgumentCaptor<Department> deptCaptor = ArgumentCaptor.forClass(Department.class);
        verify(departmentRepository, times(3)).save(deptCaptor.capture());

        List<String> deptNames = deptCaptor.getAllValues().stream()
                .map(Department::getName)
                .collect(Collectors.toList());

        assertTrue(deptNames.contains("Headquarters"), "Missing department: Headquarters");
        assertTrue(deptNames.contains("Engineering"), "Missing department: Engineering");
        assertTrue(deptNames.contains("Human Resources"), "Missing department: Human Resources");
    }

    @Test
    void run_shouldSkipWhenUsersAlreadyExist() throws Exception {
        when(userRepository.count()).thenReturn(1L);

        dataInitializer.run();

        verify(permissionRepository, never()).save(any());
        verify(departmentRepository, never()).save(any());
    }
}
