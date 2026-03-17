package com.aihiring.department.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class DepartmentResponse {
    private UUID id;
    private String name;
    private UUID parentId;
    private List<DepartmentResponse> children;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
