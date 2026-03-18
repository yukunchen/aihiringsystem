package com.aihiring;

import com.aihiring.department.Department;
import com.aihiring.department.DepartmentRepository;
import com.aihiring.role.Permission;
import com.aihiring.role.PermissionRepository;
import com.aihiring.role.Role;
import com.aihiring.role.RoleRepository;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Profile("!test")
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Data already initialized, skipping...");
            return;
        }

        log.info("Initializing seed data...");

        // Create permissions
        Permission userRead = new Permission(); userRead.setName("user:read"); userRead.setDescription("Read users");
        userRead = permissionRepository.save(userRead);
        Permission userManage = new Permission(); userManage.setName("user:manage"); userManage.setDescription("Create/update/delete users");
        userManage = permissionRepository.save(userManage);
        Permission deptRead = new Permission(); deptRead.setName("department:read"); deptRead.setDescription("Read departments");
        deptRead = permissionRepository.save(deptRead);
        Permission deptManage = new Permission(); deptManage.setName("department:manage"); deptManage.setDescription("Create/update/delete departments");
        deptManage = permissionRepository.save(deptManage);
        Permission roleRead = new Permission(); roleRead.setName("role:read"); roleRead.setDescription("Read roles");
        roleRead = permissionRepository.save(roleRead);
        Permission roleManage = new Permission(); roleManage.setName("role:manage"); roleManage.setDescription("Create/update/delete roles");
        roleManage = permissionRepository.save(roleManage);

        // Create roles
        Role superAdmin = new Role(); superAdmin.setName("SUPER_ADMIN"); superAdmin.setDescription("Super Administrator");
        superAdmin.getPermissions().addAll(permissionRepository.findAll());
        superAdmin = roleRepository.save(superAdmin);

        Role hrAdmin = new Role(); hrAdmin.setName("HR_ADMIN"); hrAdmin.setDescription("HR Administrator");
        hrAdmin.getPermissions().addAll(Arrays.asList(userRead, userManage, deptRead, deptManage, roleRead, roleManage));
        hrAdmin = roleRepository.save(hrAdmin);

        Role deptAdmin = new Role(); deptAdmin.setName("DEPT_ADMIN"); deptAdmin.setDescription("Department Administrator");
        deptAdmin.getPermissions().addAll(Arrays.asList(userRead, deptRead));
        deptAdmin = roleRepository.save(deptAdmin);

        Role user = new Role(); user.setName("USER"); user.setDescription("Regular User");
        user = roleRepository.save(user);

        roleRepository.saveAll(Arrays.asList(superAdmin, hrAdmin, deptAdmin, user));

        // Create departments
        Department hq = new Department();
        hq.setName("Headquarters");
        hq = departmentRepository.save(hq);

        Department engineering = new Department();
        engineering.setName("Engineering");
        engineering.setParent(hq);
        engineering = departmentRepository.save(engineering);

        Department hr = new Department();
        hr.setName("Human Resources");
        hr.setParent(hq);
        hr = departmentRepository.save(hr);

        // Create admin user
        User admin = new User();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setEmail("admin@aihiring.com");
        admin.setEnabled(true);
        admin.setDepartment(hq);
        admin.getRoles().add(superAdmin);
        userRepository.save(admin);

        log.info("Seed data initialized successfully!");
    }
}
