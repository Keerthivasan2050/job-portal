package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "applications", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"job_id", "candidate_user_id"})
})
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "candidate_user_id", nullable = false)
    private User candidate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resume_id")
    private Resume resume;

    @Column(columnDefinition = "TEXT")
    private String coverLetter;

    @Column(nullable = false, length = 40)
    private String status = "APPLIED"; 
    // Statuses: APPLIED, UNDER_REVIEW, SHORTLISTED, INTERVIEW_SCHEDULED, SELECTED, REJECTED, WITHDRAWN

    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    public Application() {}

    public Application(Job job, User candidate, Resume resume, String coverLetter) {
        this.job = job;
        this.candidate = candidate;
        this.resume = resume;
        this.coverLetter = coverLetter;
        this.status = "APPLIED";
        this.appliedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public User getCandidate() { return candidate; }
    public void setCandidate(User candidate) { this.candidate = candidate; }

    public Resume getResume() { return resume; }
    public void setResume(Resume resume) { this.resume = resume; }

    public String getCoverLetter() { return coverLetter; }
    public void setCoverLetter(String coverLetter) { this.coverLetter = coverLetter; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
