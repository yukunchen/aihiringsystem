package com.aihiring.department;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.aihiring.department.dto.CreateDepartmentRequest;
import com.aihiring.common.exception.BusinessException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {
    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentService departmentService;

    @Test
    void createDepartment_withValidRequest_shouldCreate() {
        CreateDepartmentRequest request = new CreateDepartmentRequest();
        request.setName("Engineering");

        when(departmentRepository.save(any(Department.class))).thenAnswer(i -> {
            Department d = i.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });

        Department result = departmentService.createDepartment(request);

        assertNotNull(result);
        assertEquals("Engineering", result.getName());
    }
}
