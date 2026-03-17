package com.aihiring.role.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateRoleRequest {
    @NotBlank
    private String name;
    private String description;
    private List<UUID> permissionIds;
}
