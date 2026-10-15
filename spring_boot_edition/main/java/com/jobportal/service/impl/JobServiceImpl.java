package com.jobportal.service.impl;

import com.jobportal.dto.JobPostDto;
import com.jobportal.dto.JobSearchCriteria;
import com.jobportal.entity.Company;
import com.jobportal.entity.Job;
import com.jobportal.entity.RecruiterProfile;
import com.jobportal.entity.User;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.UnauthorizedAccessException;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.RecruiterProfileRepository;
import com.jobportal.service.AuditLogService;
import com.jobportal.service.JobService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobServiceImpl implements JobService {

    private final JobRepository jobRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final AuditLogService auditLogService;

    public JobServiceImpl(
            JobRepository jobRepository,
            RecruiterProfileRepository recruiterProfileRepository,
            AuditLogService auditLogService) {
        this.jobRepository = jobRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public Job createJob(User recruiter, JobPostDto dto) {
        RecruiterProfile profile = recruiterProfileRepository.findByUser(recruiter)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter profile not found"));

        Company company = profile.getCompany();
        if (company == null) {
            throw new IllegalStateException("Recruiter must be linked to a company before posting jobs.");
        }

        Job job = new Job();
        job.setTitle(dto.getTitle());
        job.setDescription(dto.getDescription());
        job.setCompany(company);
        job.setRecruiter(recruiter);
        job.setLocation(dto.getLocation());
        job.setWorkArrangement(dto.getWorkArrangement() != null ? dto.getWorkArrangement() : "ONSITE");
        job.setEmploymentType(dto.getEmploymentType() != null ? dto.getEmploymentType() : "FULL_TIME");
        job.setExperienceLevel(dto.getExperienceLevel() != null ? dto.getExperienceLevel() : "MID");
        job.setMinExperienceYears(dto.getMinExperienceYears() != null ? dto.getMinExperienceYears() : 0);
        job.setSalaryMin(dto.getSalaryMin());
        job.setSalaryMax(dto.getSalaryMax());
        job.setRequiredSkills(dto.getRequiredSkills());
        job.setPreferredSkills(dto.getPreferredSkills());
        job.setQualification(dto.getQualification());
        job.setStatus("OPEN");
        job.setPostedDate(LocalDateTime.now());

        if (dto.getDeadline() != null && !dto.getDeadline().isBlank()) {
            try {
                job.setDeadline(LocalDate.parse(dto.getDeadline()));
            } catch (Exception ignored) {}
        }

        Job savedJob = jobRepository.save(job);
        auditLogService.logAction("CREATE_JOB", recruiter.getEmail(), "Job", savedJob.getId().toString(), "Created job: " + savedJob.getTitle(), "system");
        return savedJob;
    }

    @Override
    @Transactional
    public Job updateJob(Long jobId, User recruiter, JobPostDto dto) {
        Job job = findById(jobId);
        if (!job.getRecruiter().getId().equals(recruiter.getId())) {
            throw new UnauthorizedAccessException("You are not authorized to update this job listing.");
        }

        job.setTitle(dto.getTitle());
        job.setDescription(dto.getDescription());
        job.setLocation(dto.getLocation());
        job.setWorkArrangement(dto.getWorkArrangement());
        job.setEmploymentType(dto.getEmploymentType());
        job.setExperienceLevel(dto.getExperienceLevel());
        job.setMinExperienceYears(dto.getMinExperienceYears());
        job.setSalaryMin(dto.getSalaryMin());
        job.setSalaryMax(dto.getSalaryMax());
        job.setRequiredSkills(dto.getRequiredSkills());
        job.setPreferredSkills(dto.getPreferredSkills());
        job.setQualification(dto.getQualification());

        if (dto.getDeadline() != null && !dto.getDeadline().isBlank()) {
            try {
                job.setDeadline(LocalDate.parse(dto.getDeadline()));
            } catch (Exception ignored) {}
        }

        Job updatedJob = jobRepository.save(job);
        auditLogService.logAction("UPDATE_JOB", recruiter.getEmail(), "Job", updatedJob.getId().toString(), "Updated job: " + updatedJob.getTitle(), "system");
        return updatedJob;
    }

    @Override
    public Job findById(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + id));
    }

    @Override
    public Page<Job> searchJobs(JobSearchCriteria criteria) {
        Sort sort = "asc".equalsIgnoreCase(criteria.getSortDir())
                ? Sort.by(criteria.getSortBy()).ascending()
                : Sort.by(criteria.getSortBy()).descending();

        Pageable pageable = PageRequest.of(criteria.getPage(), criteria.getSize(), sort);

        String kw = (criteria.getKeyword() != null && !criteria.getKeyword().isBlank()) ? criteria.getKeyword().trim() : null;
        String loc = (criteria.getLocation() != null && !criteria.getLocation().isBlank()) ? criteria.getLocation().trim() : null;
        String wa = (criteria.getWorkArrangement() != null && !criteria.getWorkArrangement().isBlank() && !"ALL".equalsIgnoreCase(criteria.getWorkArrangement())) ? criteria.getWorkArrangement() : null;
        String et = (criteria.getEmploymentType() != null && !criteria.getEmploymentType().isBlank() && !"ALL".equalsIgnoreCase(criteria.getEmploymentType())) ? criteria.getEmploymentType() : null;
        String el = (criteria.getExperienceLevel() != null && !criteria.getExperienceLevel().isBlank() && !"ALL".equalsIgnoreCase(criteria.getExperienceLevel())) ? criteria.getExperienceLevel() : null;

        if (kw == null && loc == null && wa == null && et == null && el == null) {
            return jobRepository.findByStatus("OPEN", pageable);
        }

        return jobRepository.searchJobsAdvanced(kw, loc, wa, et, el, pageable);
    }

    @Override
    public List<Job> getJobsByRecruiter(User recruiter) {
        return jobRepository.findByRecruiterOrderByPostedDateDesc(recruiter);
    }

    @Override
    public Page<Job> getJobsByRecruiterPaged(User recruiter, Pageable pageable) {
        return jobRepository.findByRecruiterOrderByPostedDateDesc(recruiter, pageable);
    }

    @Override
    @Transactional
    public void closeJob(Long jobId, User recruiter) {
        Job job = findById(jobId);
        if (!job.getRecruiter().getId().equals(recruiter.getId())) {
            throw new UnauthorizedAccessException("You can only modify jobs posted by you.");
        }
        job.setStatus("CLOSED");
        jobRepository.save(job);
        auditLogService.logAction("CLOSE_JOB", recruiter.getEmail(), "Job", jobId.toString(), "Closed job: " + job.getTitle(), "system");
    }

    @Override
    @Transactional
    public void deleteJob(Long jobId, User user) {
        Job job = findById(jobId);
        if (!job.getRecruiter().getId().equals(user.getId()) && user.getRole() != com.jobportal.entity.Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("You are not authorized to delete this job.");
        }
        jobRepository.delete(job);
        auditLogService.logAction("DELETE_JOB", user.getEmail(), "Job", jobId.toString(), "Deleted job: " + job.getTitle(), "system");
    }

    @Override
    public List<Job> getFeaturedJobs() {
        return jobRepository.findTop6ByStatusOrderByPostedDateDesc("OPEN");
    }

    @Override
    public List<Job> getAllJobs() {
        return jobRepository.findAll();
    }

    @Override
    @Transactional
    public void moderateJob(Long jobId, String status, User admin) {
        Job job = findById(jobId);
        job.setStatus(status);
        jobRepository.save(job);
        auditLogService.logAction("MODERATE_JOB", admin.getEmail(), "Job", jobId.toString(), "Admin updated status to " + status, "system");
    }
}
