package com.aihiring.user.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateUserRequest {
    @Email
    private String email;
    private Boolean enabled;
    private UUID departmentId;
    private List<UUID> roleIds;
}
