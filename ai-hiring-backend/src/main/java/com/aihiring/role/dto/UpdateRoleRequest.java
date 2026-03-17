package com.aihiring.role.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateRoleRequest {
    private String name;
    private String description;
    private List<UUID> permissionIds;
}
