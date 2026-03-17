package com.aihiring.department.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdateDepartmentRequest {
    private String name;
    private UUID parentId;
}
