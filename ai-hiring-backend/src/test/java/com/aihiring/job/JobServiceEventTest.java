package com.aihiring.job;

import com.aihiring.job.dto.CreateJobRequest;
import com.aihiring.job.dto.UpdateJobRequest;
import com.aihiring.department.DepartmentRepository;
import com.aihiring.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobServiceEventTest {

    @Mock JobRepository jobRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks JobService jobService;

    @Test
    void create_publishesJobDescriptionSavedEvent() {
        var dept = new com.aihiring.department.Department();
        dept.setId(UUID.randomUUID());
        dept.setName("Engineering");

        var user = new com.aihiring.user.User();
        user.setId(UUID.randomUUID());

        var savedJob = new JobDescription();
        savedJob.setId(UUID.randomUUID());
        savedJob.setTitle("Engineer");
        savedJob.setDescription("Build things");
        savedJob.setRequirements("5yr");
        savedJob.setSkills("[\"Java\"]");
        savedJob.setDepartment(dept);
        savedJob.setCreatedBy(user);

        when(departmentRepository.findById(any())).thenReturn(Optional.of(dept));
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(jobRepository.save(any())).thenReturn(savedJob);

        var request = new CreateJobRequest();
        request.setTitle("Engineer");
        request.setDescription("Build things");
        request.setRequirements("5yr");
        request.setSkills("[\"Java\"]");
        request.setDepartmentId(dept.getId());

        jobService.create(request, user.getId());

        var captor = ArgumentCaptor.forClass(JobDescriptionSavedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.getJobId()).isEqualTo(savedJob.getId());
        assertThat(event.getTitle()).isEqualTo("Engineer");
        assertThat(event.getDescription()).isEqualTo("Build things");
    }

    @Test
    void update_publishesJobDescriptionSavedEvent() {
        var dept = new com.aihiring.department.Department();
        dept.setId(UUID.randomUUID());
        dept.setName("Engineering");

        var user = new com.aihiring.user.User();
        user.setId(UUID.randomUUID());

        var existingJob = new JobDescription();
        existingJob.setId(UUID.randomUUID());
        existingJob.setTitle("Old title");
        existingJob.setDescription("Old desc");
        existingJob.setDepartment(dept);
        existingJob.setCreatedBy(user);

        when(jobRepository.findById(any())).thenReturn(Optional.of(existingJob));
        when(jobRepository.save(any())).thenReturn(existingJob);

        var request = new UpdateJobRequest();
        request.setTitle("New title");

        jobService.update(existingJob.getId(), request);

        verify(eventPublisher).publishEvent(any(JobDescriptionSavedEvent.class));
    }
}
