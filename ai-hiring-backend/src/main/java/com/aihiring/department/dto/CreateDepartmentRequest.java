package com.aihiring.department.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateDepartmentRequest {
    @NotBlank
    private String name;
    private UUID parentId;
}
