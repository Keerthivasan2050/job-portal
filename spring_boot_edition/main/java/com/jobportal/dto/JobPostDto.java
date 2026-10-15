package com.jobportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class JobPostDto {

    private Long id;

    @NotBlank(message = "Job title is required")
    @Size(min = 3, max = 150, message = "Title must be between 3 and 150 characters")
    private String title;

    @NotBlank(message = "Job description is required")
    private String description;

    @NotBlank(message = "Location is required")
    private String location;

    private String workArrangement = "ONSITE"; // REMOTE, HYBRID, ONSITE
    private String employmentType = "FULL_TIME"; // FULL_TIME, PART_TIME, CONTRACT, INTERNSHIP
    private String experienceLevel = "MID"; // ENTRY, MID, SENIOR, LEAD

    private Integer minExperienceYears = 0;
    private Double salaryMin;
    private Double salaryMax;

    @NotBlank(message = "Required skills are required (comma separated)")
    private String requiredSkills;

    private String preferredSkills;
    private String qualification;
    private String deadline; // YYYY-MM-DD

    public JobPostDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

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

    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
}
