package com.aihiring.auth;

import com.aihiring.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Temporary debug controller to diagnose permission loading issues.
 * Remove after issue #57 is resolved.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthDebugController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/debug/permissions")
    public ApiResponse<List<Map<String, Object>>> debugPermissions() {
        // Show all permissions
        List<Map<String, Object>> permissions = jdbcTemplate.queryForList("SELECT id, name FROM permissions ORDER BY name");

        // Show SUPER_ADMIN role_permissions
        List<Map<String, Object>> rolePerms = jdbcTemplate.queryForList("""
            SELECT r.name as role_name, p.name as perm_name, p.id as perm_id
            FROM role_permissions rp
            JOIN roles r ON rp.role_id = r.id
            JOIN permissions p ON rp.permission_id = p.id
            WHERE r.name = 'SUPER_ADMIN'
            ORDER BY p.name
            """);

        // Show role counts
        List<Map<String, Object>> roleCounts = jdbcTemplate.queryForList("""
            SELECT r.name as role_name, COUNT(rp.permission_id) as perm_count
            FROM roles r
            LEFT JOIN role_permissions rp ON r.id = rp.role_id
            GROUP BY r.name
            ORDER BY r.name
            """);

        return ApiResponse.success(List.of(
                Map.of("section", "all_permissions", "data", permissions),
                Map.of("section", "super_admin_permissions", "data", rolePerms),
                Map.of("section", "role_permission_counts", "data", roleCounts)
        ));
    }
}
