package com.jobportal.repository;

import com.jobportal.entity.Job;
import com.jobportal.entity.JobReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobReportRepository extends JpaRepository<JobReport, Long> {
    List<JobReport> findByStatusOrderByCreatedAtDesc(String status);
    List<JobReport> findAllByOrderByCreatedAtDesc();
    List<JobReport> findByJob(Job job);
    long countByStatus(String status);
}
