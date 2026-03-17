package com.aihiring.user.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private boolean enabled;
    private DepartmentDto department;
    private List<RoleDto> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class DepartmentDto {
        private UUID id;
        private String name;
    }

    @Data
    public static class RoleDto {
        private UUID id;
        private String name;
    }
}
