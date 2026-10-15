package com.jobportal.dto;

public class JobSeekerProfileDto {

    private String fullName;
    private String email;
    private String phone;
    private String headline;
    private String bio;
    private String skills;
    private String qualification;
    private Double experienceYears;
    private String preferredTitle;
    private String preferredLocation;
    private Double expectedSalary;
    private String workArrangementPreference;
    private String employmentTypePreference;

    public JobSeekerProfileDto() {}

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

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
