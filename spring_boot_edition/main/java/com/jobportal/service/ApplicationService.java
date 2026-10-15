package com.jobportal.service;

import com.jobportal.entity.Application;
import com.jobportal.entity.ApplicationStatusHistory;
import com.jobportal.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ApplicationService {
    Application applyForJob(Long jobId, User candidate, Long resumeId, String coverLetter);
    List<Application> getApplicationsByCandidate(User candidate);
    Page<Application> getApplicationsByCandidatePaged(User candidate, Pageable pageable);
    List<Application> getApplicationsByJob(Long jobId, User recruiter);
    Page<Application> getApplicationsByJobPaged(Long jobId, User recruiter, Pageable pageable);
    Application getApplicationById(Long applicationId);
    void updateApplicationStatus(Long applicationId, String newStatus, User actor, String note);
    void withdrawApplication(Long applicationId, User candidate);
    boolean hasApplied(Long jobId, User candidate);
    List<ApplicationStatusHistory> getStatusHistory(Long applicationId);
}
