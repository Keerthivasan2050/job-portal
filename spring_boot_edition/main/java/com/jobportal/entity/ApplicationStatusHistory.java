package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "application_status_history")
public class ApplicationStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(nullable = false, length = 40)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public ApplicationStatusHistory() {}

    public ApplicationStatusHistory(Application application, String status, User changedBy, String notes) {
        this.application = application;
        this.status = status;
        this.changedBy = changedBy;
        this.notes = notes;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public User getChangedBy() { return changedBy; }
    public void setChangedBy(User changedBy) { this.changedBy = changedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
