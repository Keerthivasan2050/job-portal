package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User recipient;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(length = 50)
    private String type = "INFO"; // APPLICATION, INTERVIEW, JOB_ALERT, VERIFICATION, SYSTEM

    @Column(length = 255)
    private String link;

    @Column(nullable = false)
    private boolean isRead = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Notification() {}

    public Notification(User recipient, String title, String message, String type, String link) {
        this.recipient = recipient;
        this.title = title;
        this.message = message;
        this.type = type;
        this.link = link;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getRecipient() { return recipient; }
    public void setRecipient(User recipient) { this.recipient = recipient; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
