package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_reports")
public class JobReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by_user_id")
    private User reportedBy;

    @Column(length = 150)
    private String reporterEmail;

    @Column(nullable = false, length = 100)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, UNDER_INVESTIGATION, RESOLVED, DISMISSED

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime resolvedAt;

    public JobReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public User getReportedBy() { return reportedBy; }
    public void setReportedBy(User reportedBy) { this.reportedBy = reportedBy; }

    public String getReporterEmail() { return reporterEmail; }
    public void setReporterEmail(String reporterEmail) { this.reporterEmail = reporterEmail; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
