package com.jobportal.service.impl;

import com.jobportal.entity.*;
import com.jobportal.exception.DuplicateApplicationException;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.exception.UnauthorizedAccessException;
import com.jobportal.repository.ApplicationRepository;
import com.jobportal.repository.ApplicationStatusHistoryRepository;
import com.jobportal.repository.JobRepository;
import com.jobportal.repository.ResumeRepository;
import com.jobportal.service.ApplicationService;
import com.jobportal.service.AuditLogService;
import com.jobportal.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApplicationServiceImpl implements ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public ApplicationServiceImpl(
            ApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            JobRepository jobRepository,
            ResumeRepository resumeRepository,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.applicationRepository = applicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.jobRepository = jobRepository;
        this.resumeRepository = resumeRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public Application applyForJob(Long jobId, User candidate, Long resumeId, String coverLetter) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found with id: " + jobId));

        if (!"OPEN".equalsIgnoreCase(job.getStatus())) {
            throw new IllegalStateException("This job listing is no longer accepting applications.");
        }

        if (applicationRepository.existsByJobAndCandidate(job, candidate)) {
            throw new DuplicateApplicationException("You have already applied for the position: " + job.getTitle());
        }

        Resume resume = null;
        if (resumeId != null) {
            resume = resumeRepository.findById(resumeId).orElse(null);
            if (resume != null && !resume.getUser().getId().equals(candidate.getId())) {
                throw new UnauthorizedAccessException("Cannot attach another user's resume.");
            }
        }

        Application application = new Application(job, candidate, resume, coverLetter);
        Application savedApplication = applicationRepository.save(application);

        ApplicationStatusHistory history = new ApplicationStatusHistory(
                savedApplication,
                "APPLIED",
                candidate,
                "Application submitted by " + candidate.getFullName()
        );
        statusHistoryRepository.save(history);

        // Notify Job Seeker
        notificationService.sendNotification(
                candidate,
                "Application Submitted",
                "Your application for " + job.getTitle() + " at " + job.getCompany().getName() + " was successfully received.",
                "APPLICATION",
                "/seeker/applications"
        );

        // Notify Recruiter
        if (job.getRecruiter() != null) {
            notificationService.sendNotification(
                    job.getRecruiter(),
                    "New Candidate Application",
                    candidate.getFullName() + " applied for " + job.getTitle(),
                    "APPLICATION",
                    "/recruiter/jobs/" + job.getId() + "/applicants"
            );
        }

        auditLogService.logAction("JOB_APPLICATION", candidate.getEmail(), "Application", savedApplication.getId().toString(), "Applied for " + job.getTitle(), "system");
        return savedApplication;
    }

    @Override
    public List<Application> getApplicationsByCandidate(User candidate) {
        return applicationRepository.findByCandidateOrderByAppliedAtDesc(candidate);
    }

    @Override
    public Page<Application> getApplicationsByCandidatePaged(User candidate, Pageable pageable) {
        return applicationRepository.findByCandidateOrderByAppliedAtDesc(candidate, pageable);
    }

    @Override
    public List<Application> getApplicationsByJob(Long jobId, User recruiter) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getRecruiter().getId().equals(recruiter.getId()) && recruiter.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("Unauthorized to view applications for this listing.");
        }

        return applicationRepository.findByJobOrderByAppliedAtDesc(job);
    }

    @Override
    public Page<Application> getApplicationsByJobPaged(Long jobId, User recruiter, Pageable pageable) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));

        if (!job.getRecruiter().getId().equals(recruiter.getId()) && recruiter.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("Unauthorized to view applications for this listing.");
        }

        return applicationRepository.findByJobOrderByAppliedAtDesc(job, pageable);
    }

    @Override
    public Application getApplicationById(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + applicationId));
    }

    @Override
    @Transactional
    public void updateApplicationStatus(Long applicationId, String newStatus, User actor, String note) {
        Application application = getApplicationById(applicationId);
        Job job = application.getJob();

        if (!job.getRecruiter().getId().equals(actor.getId()) && actor.getRole() != Role.ROLE_ADMIN) {
            throw new UnauthorizedAccessException("You are not permitted to change status for this applicant.");
        }

        application.setStatus(newStatus);
        applicationRepository.save(application);

        ApplicationStatusHistory history = new ApplicationStatusHistory(
                application,
                newStatus,
                actor,
                note != null ? note : "Status transitioned to " + newStatus
        );
        statusHistoryRepository.save(history);

        // Notify candidate
        notificationService.sendNotification(
                application.getCandidate(),
                "Application Status Updated",
                "Your application for " + job.getTitle() + " has been updated to: " + newStatus,
                "APPLICATION",
                "/seeker/applications"
        );

        auditLogService.logAction("APPLICATION_STATUS_UPDATE", actor.getEmail(), "Application", applicationId.toString(), "Status changed to " + newStatus, "system");
    }

    @Override
    @Transactional
    public void withdrawApplication(Long applicationId, User candidate) {
        Application application = getApplicationById(applicationId);
        if (!application.getCandidate().getId().equals(candidate.getId())) {
            throw new UnauthorizedAccessException("You can only withdraw your own applications.");
        }

        application.setStatus("WITHDRAWN");
        applicationRepository.save(application);

        ApplicationStatusHistory history = new ApplicationStatusHistory(
                application,
                "WITHDRAWN",
                candidate,
                "Candidate voluntarily withdrew the application"
        );
        statusHistoryRepository.save(history);

        notificationService.sendNotification(
                candidate,
                "Application Withdrawn",
                "You withdrew your application for " + application.getJob().getTitle(),
                "APPLICATION",
                "/seeker/applications"
        );
    }

    @Override
    public boolean hasApplied(Long jobId, User candidate) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return false;
        return applicationRepository.existsByJobAndCandidate(job, candidate);
    }

    @Override
    public List<ApplicationStatusHistory> getStatusHistory(Long applicationId) {
        Application app = getApplicationById(applicationId);
        return statusHistoryRepository.findByApplicationOrderByTimestampAsc(app);
    }
}
