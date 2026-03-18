package com.aihiring.job;

import com.aihiring.common.exception.BusinessException;
import com.aihiring.common.exception.ResourceNotFoundException;
import com.aihiring.department.DepartmentRepository;
import com.aihiring.job.dto.ChangeStatusRequest;
import com.aihiring.job.dto.CreateJobRequest;
import com.aihiring.job.dto.UpdateJobRequest;
import com.aihiring.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = Map.of(
        JobStatus.DRAFT, Set.of(JobStatus.PUBLISHED),
        JobStatus.PUBLISHED, Set.of(JobStatus.PAUSED, JobStatus.CLOSED),
        JobStatus.PAUSED, Set.of(JobStatus.PUBLISHED, JobStatus.CLOSED),
        JobStatus.CLOSED, Set.of()
    );

    private static final Set<JobStatus> DELETABLE_STATUSES = Set.of(JobStatus.DRAFT, JobStatus.CLOSED);

    @Transactional
    public JobDescription create(CreateJobRequest request, UUID createdByUserId) {
        var department = departmentRepository.findById(request.getDepartmentId())
            .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        var user = userRepository.findById(createdByUserId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        JobDescription job = new JobDescription();
        job.setTitle(request.getTitle());
        job.setDescription(request.getDescription());
        job.setRequirements(request.getRequirements());
        job.setSkills(request.getSkills());
        job.setEducation(request.getEducation());
        job.setExperience(request.getExperience());
        job.setSalaryRange(request.getSalaryRange());
        job.setLocation(request.getLocation());
        job.setStatus(JobStatus.DRAFT);
        job.setDepartment(department);
        job.setCreatedBy(user);

        return jobRepository.save(job);
    }

    public JobDescription getById(UUID id) {
        return jobRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Job description not found"));
    }

    @Transactional
    public JobDescription update(UUID id, UpdateJobRequest request) {
        JobDescription job = getById(id);
        if (request.getTitle() != null) job.setTitle(request.getTitle());
        if (request.getDescription() != null) job.setDescription(request.getDescription());
        if (request.getRequirements() != null) job.setRequirements(request.getRequirements());
        if (request.getSkills() != null) job.setSkills(request.getSkills());
        if (request.getEducation() != null) job.setEducation(request.getEducation());
        if (request.getExperience() != null) job.setExperience(request.getExperience());
        if (request.getSalaryRange() != null) job.setSalaryRange(request.getSalaryRange());
        if (request.getLocation() != null) job.setLocation(request.getLocation());
        if (request.getDepartmentId() != null) {
            var department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found"));
            job.setDepartment(department);
        }
        return jobRepository.save(job);
    }

    @Transactional
    public JobDescription changeStatus(UUID id, ChangeStatusRequest request) {
        JobDescription job = getById(id);
        JobStatus currentStatus = job.getStatus();
        JobStatus targetStatus = request.getStatus();
        Set<JobStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowed.contains(targetStatus)) {
            throw new BusinessException(400,
                "Invalid status transition from " + currentStatus + " to " + targetStatus);
        }
        job.setStatus(targetStatus);
        return jobRepository.save(job);
    }

    @Transactional
    public void delete(UUID id) {
        JobDescription job = getById(id);
        if (!DELETABLE_STATUSES.contains(job.getStatus())) {
            throw new BusinessException(400,
                "Cannot delete job with status " + job.getStatus() + ". Only DRAFT and CLOSED jobs can be deleted");
        }
        jobRepository.delete(job);
    }

    public Page<JobDescription> list(String keyword, JobStatus status, UUID departmentId, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return jobRepository.searchByKeyword(keyword, pageable);
        }
        if (status != null && departmentId != null) {
            return jobRepository.findByStatusAndDepartmentId(status, departmentId, pageable);
        }
        if (status != null) {
            return jobRepository.findByStatus(status, pageable);
        }
        if (departmentId != null) {
            return jobRepository.findByDepartmentId(departmentId, pageable);
        }
        return jobRepository.findAll(pageable);
    }
}
