package com.jobportal.service;

import com.jobportal.dto.JobReportDto;
import com.jobportal.entity.JobReport;
import com.jobportal.entity.User;

import java.util.List;

public interface JobReportService {
    JobReport reportJob(JobReportDto dto, User user);
    List<JobReport> getPendingReports();
    List<JobReport> getAllReports();
    void resolveReport(Long reportId, String status, String adminNotes, User admin);
}
