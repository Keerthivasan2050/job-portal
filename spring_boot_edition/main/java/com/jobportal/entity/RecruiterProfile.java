package com.jobportal.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(length = 100)
    private String designation;

    @Column(length = 100)
    private String department;

    @Column(length = 25)
    private String officialPhone;

    @Column(nullable = false)
    private boolean verified = false;

    public RecruiterProfile() {}

    public RecruiterProfile(User user, Company company, String designation) {
        this.user = user;
        this.company = company;
        this.designation = designation;
        this.verified = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getOfficialPhone() { return officialPhone; }
    public void setOfficialPhone(String officialPhone) { this.officialPhone = officialPhone; }

    public boolean isVerified() { return verified; }
    public void setVerified(boolean verified) { this.verified = verified; }
}
