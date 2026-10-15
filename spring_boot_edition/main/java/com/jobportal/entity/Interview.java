package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "interviews")
public class Interview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "candidate_user_id", nullable = false)
    private User candidate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recruiter_user_id", nullable = false)
    private User recruiter;

    @Column(nullable = false)
    private LocalDateTime interviewDate;

    @Column(nullable = false, length = 50)
    private String interviewType = "ONLINE_VIDEO"; // ONLINE_VIDEO, TECHNICAL_ROUND, HR_ROUND, ON_SITE

    @Column(length = 255)
    private String meetingLink;

    @Column(length = 150)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 30)
    private String status = "SCHEDULED"; // SCHEDULED, COMPLETED, CANCELLED, RESCHEDULED

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Interview() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public Job getJob() { return job; }
    public void setJob(Job job) { this.job = job; }

    public User getCandidate() { return candidate; }
    public void setCandidate(User candidate) { this.candidate = candidate; }

    public User getRecruiter() { return recruiter; }
    public void setRecruiter(User recruiter) { this.recruiter = recruiter; }

    public LocalDateTime getInterviewDate() { return interviewDate; }
    public void setInterviewDate(LocalDateTime interviewDate) { this.interviewDate = interviewDate; }

    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }

    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
