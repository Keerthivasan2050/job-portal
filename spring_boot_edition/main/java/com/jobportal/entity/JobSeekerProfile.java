package com.jobportal.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "job_seeker_profiles")
public class JobSeekerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 150)
    private String headline;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(columnDefinition = "TEXT")
    private String skills;

    @Column(length = 200)
    private String qualification;

    private Double experienceYears = 0.0;

    @Column(length = 150)
    private String preferredTitle;

    @Column(length = 100)
    private String preferredLocation;

    private Double expectedSalary;

    @Column(length = 50)
    private String workArrangementPreference = "ANY"; // REMOTE, HYBRID, ONSITE, ANY

    @Column(length = 50)
    private String employmentTypePreference = "FULL_TIME"; // FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP

    public JobSeekerProfile() {}

    public JobSeekerProfile(User user) {
        this.user = user;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getSkills() { return skills; }
    public void setSkills(String skills) { this.skills = skills; }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }

    public Double getExperienceYears() { return experienceYears; }
    public void setExperienceYears(Double experienceYears) { this.experienceYears = experienceYears; }

    public String getPreferredTitle() { return preferredTitle; }
    public void setPreferredTitle(String preferredTitle) { this.preferredTitle = preferredTitle; }

    public String getPreferredLocation() { return preferredLocation; }
    public void setPreferredLocation(String preferredLocation) { this.preferredLocation = preferredLocation; }

    public Double getExpectedSalary() { return expectedSalary; }
    public void setExpectedSalary(Double expectedSalary) { this.expectedSalary = expectedSalary; }

    public String getWorkArrangementPreference() { return workArrangementPreference; }
    public void setWorkArrangementPreference(String workArrangementPreference) { this.workArrangementPreference = workArrangementPreference; }

    public String getEmploymentTypePreference() { return employmentTypePreference; }
    public void setEmploymentTypePreference(String employmentTypePreference) { this.employmentTypePreference = employmentTypePreference; }
}
