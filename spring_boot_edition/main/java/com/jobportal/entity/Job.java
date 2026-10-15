package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recruiter_user_id", nullable = false)
    private User recruiter;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(nullable = false, length = 50)
    private String workArrangement = "ONSITE"; // REMOTE, HYBRID, ONSITE

    @Column(nullable = false, length = 50)
    private String employmentType = "FULL_TIME"; // FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP

    @Column(nullable = false, length = 50)
    private String experienceLevel = "MID"; // ENTRY, MID, SENIOR, LEAD

    private Integer minExperienceYears = 0;

    private Double salaryMin;
    private Double salaryMax;

    @Column(columnDefinition = "TEXT")
    private String requiredSkills;

    @Column(columnDefinition = "TEXT")
    private String preferredSkills;

    @Column(length = 200)
    private String qualification;

    @Column(nullable = false, length = 30)
    private String status = "OPEN"; // OPEN, CLOSED, MODERATED

    @Column(nullable = false, updatable = false)
    private LocalDateTime postedDate = LocalDateTime.now();

    private LocalDate deadline;

    public Job() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public User getRecruiter() { return recruiter; }
    public void setRecruiter(User recruiter) { this.recruiter = recruiter; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getWorkArrangement() { return workArrangement; }
    public void setWorkArrangement(String workArrangement) { this.workArrangement = workArrangement; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public Integer getMinExperienceYears() { return minExperienceYears; }
    public void setMinExperienceYears(Integer minExperienceYears) { this.minExperienceYears = minExperienceYears; }

    public Double getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Double salaryMin) { this.salaryMin = salaryMin; }

    public Double getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Double salaryMax) { this.salaryMax = salaryMax; }

    public String getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(String requiredSkills) { this.requiredSkills = requiredSkills; }

    public String getPreferredSkills() { return preferredSkills; }
    public void setPreferredSkills(String preferredSkills) { this.preferredSkills = preferredSkills; }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getPostedDate() { return postedDate; }
    public void setPostedDate(LocalDateTime postedDate) { this.postedDate = postedDate; }

    public LocalDate getDeadline() { return deadline; }
    public void setDeadline(LocalDate deadline) { this.deadline = deadline; }
}
