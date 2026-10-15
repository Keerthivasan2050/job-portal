package com.jobportal.service;

import com.jobportal.dto.JobPostDto;
import com.jobportal.dto.JobSearchCriteria;
import com.jobportal.entity.Job;
import com.jobportal.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface JobService {
    Job createJob(User recruiter, JobPostDto dto);
    Job updateJob(Long jobId, User recruiter, JobPostDto dto);
    Job findById(Long id);
    Page<Job> searchJobs(JobSearchCriteria criteria);
    List<Job> getJobsByRecruiter(User recruiter);
    Page<Job> getJobsByRecruiterPaged(User recruiter, Pageable pageable);
    void closeJob(Long jobId, User recruiter);
    void deleteJob(Long jobId, User user);
    List<Job> getFeaturedJobs();
    List<Job> getAllJobs();
    void moderateJob(Long jobId, String status, User admin);
}
