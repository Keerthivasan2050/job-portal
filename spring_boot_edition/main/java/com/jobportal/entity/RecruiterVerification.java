package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recruiter_verifications")
public class RecruiterVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recruiter_user_id", nullable = false)
    private User recruiter;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String businessEmail;

    @Column(length = 100)
    private String companyRegistrationNumber;

    @Column(length = 255)
    private String documentUrl;

    @Column(nullable = false, length = 30)
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    @Column(nullable = false, updatable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedBy;

    public RecruiterVerification() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getRecruiter() { return recruiter; }
    public void setRecruiter(User recruiter) { this.recruiter = recruiter; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getBusinessEmail() { return businessEmail; }
    public void setBusinessEmail(String businessEmail) { this.businessEmail = businessEmail; }

    public String getCompanyRegistrationNumber() { return companyRegistrationNumber; }
    public void setCompanyRegistrationNumber(String companyRegistrationNumber) { this.companyRegistrationNumber = companyRegistrationNumber; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }

    public User getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(User reviewedBy) { this.reviewedBy = reviewedBy; }
}
