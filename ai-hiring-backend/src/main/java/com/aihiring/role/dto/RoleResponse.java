package com.aihiring.role.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class RoleResponse {
    private UUID id;
    private String name;
    private String description;
    private List<PermissionDto> permissions;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    public static class PermissionDto {
        private UUID id;
        private String name;
    }
}
