package com.aihiring.job;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.department.Department;
import com.aihiring.department.DepartmentRepository;
import com.aihiring.job.dto.ChangeStatusRequest;
import com.aihiring.job.dto.CreateJobRequest;
import com.aihiring.job.dto.UpdateJobRequest;
import com.aihiring.user.User;
import com.aihiring.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private JobService jobService;

    private Department createDepartment() {
        Department dept = new Department();
        dept.setId(UUID.randomUUID());
        dept.setName("Engineering");
        return dept;
    }

    private User createUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        return user;
    }

    private JobDescription createJob(JobStatus status) {
        JobDescription job = new JobDescription();
        job.setId(UUID.randomUUID());
        job.setTitle("Senior Java Developer");
        job.setDescription("Looking for a senior Java developer");
        job.setStatus(status);
        job.setDepartment(createDepartment());
        job.setCreatedBy(createUser());
        return job;
    }

    // ===== Create Tests =====

    @Test
    void create_withValidRequest_shouldSetDraftStatusAndAssociations() {
        Department dept = createDepartment();
        User user = createUser();
        CreateJobRequest request = new CreateJobRequest();
        request.setTitle("Senior Java Developer");
        request.setDescription("Looking for a senior Java developer");
        request.setDepartmentId(dept.getId());

        when(departmentRepository.findById(dept.getId())).thenReturn(Optional.of(dept));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(i -> {
            JobDescription j = i.getArgument(0);
            j.setId(UUID.randomUUID());
            return j;
        });

        JobDescription result = jobService.create(request, user.getId());

        assertEquals(JobStatus.DRAFT, result.getStatus());
        assertEquals(dept, result.getDepartment());
        assertEquals(user, result.getCreatedBy());
        assertEquals("Senior Java Developer", result.getTitle());
        verify(jobRepository).save(any(JobDescription.class));
    }

    @Test
    void create_withNonExistentDepartment_shouldThrowResourceNotFoundException() {
        UUID deptId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateJobRequest request = new CreateJobRequest();
        request.setTitle("Senior Java Developer");
        request.setDescription("Description");
        request.setDepartmentId(deptId);

        when(departmentRepository.findById(deptId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> jobService.create(request, userId));
        verify(jobRepository, never()).save(any());
    }

    // ===== Get Tests =====

    @Test
    void getById_withExistingId_shouldReturnJob() {
        JobDescription job = createJob(JobStatus.DRAFT);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        JobDescription result = jobService.getById(job.getId());

        assertEquals(job.getId(), result.getId());
    }

    @Test
    void getById_withNonExistentId_shouldThrowResourceNotFoundException() {
        UUID id = UUID.randomUUID();
        when(jobRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> jobService.getById(id));
    }

    // ===== Update Tests =====

    @Test
    void update_withNonNullFields_shouldOnlyUpdateProvidedFields() {
        JobDescription job = createJob(JobStatus.DRAFT);
        String originalDescription = job.getDescription();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(i -> i.getArgument(0));

        UpdateJobRequest request = new UpdateJobRequest();
        request.setTitle("Updated Title");
        // description not set — should remain unchanged

        JobDescription result = jobService.update(job.getId(), request);

        assertEquals("Updated Title", result.getTitle());
        assertEquals(originalDescription, result.getDescription());
    }

    @Test
    void update_withDepartmentChange_shouldUpdateDepartment() {
        JobDescription job = createJob(JobStatus.DRAFT);
        Department newDept = createDepartment();
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(departmentRepository.findById(newDept.getId())).thenReturn(Optional.of(newDept));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(i -> i.getArgument(0));

        UpdateJobRequest request = new UpdateJobRequest();
        request.setDepartmentId(newDept.getId());

        JobDescription result = jobService.update(job.getId(), request);

        assertEquals(newDept, result.getDepartment());
    }

    // ===== Status Transition Tests =====

    @Test
    void changeStatus_draftToPublished_shouldSucceed() {
        JobDescription job = createJob(JobStatus.DRAFT);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(i -> i.getArgument(0));

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(JobStatus.PUBLISHED);

        JobDescription result = jobService.changeStatus(job.getId(), request);

        assertEquals(JobStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void changeStatus_publishedToPaused_shouldSucceed() {
        JobDescription job = createJob(JobStatus.PUBLISHED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(i -> i.getArgument(0));

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(JobStatus.PAUSED);

        JobDescription result = jobService.changeStatus(job.getId(), request);

        assertEquals(JobStatus.PAUSED, result.getStatus());
    }

    @Test
    void changeStatus_publishedToClosed_shouldSucceed() {
        JobDescription job = createJob(JobStatus.PUBLISHED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(i -> i.getArgument(0));

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(JobStatus.CLOSED);

        JobDescription result = jobService.changeStatus(job.getId(), request);

        assertEquals(JobStatus.CLOSED, result.getStatus());
    }

    @Test
    void changeStatus_pausedToPublished_shouldSucceed() {
        JobDescription job = createJob(JobStatus.PAUSED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(jobRepository.save(any(JobDescription.class))).thenAnswer(i -> i.getArgument(0));

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(JobStatus.PUBLISHED);

        JobDescription result = jobService.changeStatus(job.getId(), request);

        assertEquals(JobStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void changeStatus_draftToClosed_shouldThrowBusinessException() {
        JobDescription job = createJob(JobStatus.DRAFT);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(JobStatus.CLOSED);

        assertThrows(BusinessException.class, () -> jobService.changeStatus(job.getId(), request));
        verify(jobRepository, never()).save(any());
    }

    @Test
    void changeStatus_closedToPublished_shouldThrowBusinessException() {
        JobDescription job = createJob(JobStatus.CLOSED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        ChangeStatusRequest request = new ChangeStatusRequest();
        request.setStatus(JobStatus.PUBLISHED);

        assertThrows(BusinessException.class, () -> jobService.changeStatus(job.getId(), request));
        verify(jobRepository, never()).save(any());
    }

    // ===== Delete Tests =====

    @Test
    void delete_draftJob_shouldSucceed() {
        JobDescription job = createJob(JobStatus.DRAFT);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        jobService.delete(job.getId());

        verify(jobRepository).delete(job);
    }

    @Test
    void delete_closedJob_shouldSucceed() {
        JobDescription job = createJob(JobStatus.CLOSED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        jobService.delete(job.getId());

        verify(jobRepository).delete(job);
    }

    @Test
    void delete_publishedJob_shouldThrowBusinessException() {
        JobDescription job = createJob(JobStatus.PUBLISHED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThrows(BusinessException.class, () -> jobService.delete(job.getId()));
        verify(jobRepository, never()).delete(any());
    }

    @Test
    void delete_pausedJob_shouldThrowBusinessException() {
        JobDescription job = createJob(JobStatus.PAUSED);
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThrows(BusinessException.class, () -> jobService.delete(job.getId()));
        verify(jobRepository, never()).delete(any());
    }

    // ===== List Tests =====

    @Test
    void list_withKeyword_shouldSearchByKeyword() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(jobRepository.searchByKeyword("Java", pageable)).thenReturn(new PageImpl<>(List.of()));

        jobService.list("Java", null, null, pageable);

        verify(jobRepository).searchByKeyword("Java", pageable);
    }

    @Test
    void list_withStatusFilter_shouldFilterByStatus() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(jobRepository.findByStatus(JobStatus.PUBLISHED, pageable)).thenReturn(new PageImpl<>(List.of()));

        jobService.list(null, JobStatus.PUBLISHED, null, pageable);

        verify(jobRepository).findByStatus(JobStatus.PUBLISHED, pageable);
    }

    @Test
    void list_withDepartmentFilter_shouldFilterByDepartment() {
        UUID deptId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        when(jobRepository.findByDepartmentId(deptId, pageable)).thenReturn(new PageImpl<>(List.of()));

        jobService.list(null, null, deptId, pageable);

        verify(jobRepository).findByDepartmentId(deptId, pageable);
    }

    @Test
    void list_withStatusAndDepartment_shouldFilterByBoth() {
        UUID deptId = UUID.randomUUID();
        PageRequest pageable = PageRequest.of(0, 20);
        when(jobRepository.findByStatusAndDepartmentId(JobStatus.PUBLISHED, deptId, pageable))
            .thenReturn(new PageImpl<>(List.of()));

        jobService.list(null, JobStatus.PUBLISHED, deptId, pageable);

        verify(jobRepository).findByStatusAndDepartmentId(JobStatus.PUBLISHED, deptId, pageable);
    }

    @Test
    void list_withNoFilters_shouldReturnAll() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(jobRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        jobService.list(null, null, null, pageable);

        verify(jobRepository).findAll(pageable);
    }
}
