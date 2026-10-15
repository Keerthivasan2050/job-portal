package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 25)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 255)
    private String resetPasswordToken;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private JobSeekerProfile jobSeekerProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private RecruiterProfile recruiterProfile;

    public User() {}

    public User(String email, String password, String fullName, String phone, Role role) {
        this.email = email;
        this.password = password;
        this.fullName = fullName;
        this.phone = phone;
        this.role = role;
        this.enabled = true;
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getResetPasswordToken() { return resetPasswordToken; }
    public void setResetPasswordToken(String resetPasswordToken) { this.resetPasswordToken = resetPasswordToken; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public JobSeekerProfile getJobSeekerProfile() { return jobSeekerProfile; }
    public void setJobSeekerProfile(JobSeekerProfile jobSeekerProfile) { this.jobSeekerProfile = jobSeekerProfile; }

    public RecruiterProfile getRecruiterProfile() { return recruiterProfile; }
    public void setRecruiterProfile(RecruiterProfile recruiterProfile) { this.recruiterProfile = recruiterProfile; }
}
