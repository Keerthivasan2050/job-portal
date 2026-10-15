package com.jobportal.dto;

import jakarta.validation.constraints.NotBlank;

public class CompanyProfileDto {

    @NotBlank(message = "Company name is required")
    private String name;

    private String industry;
    private String location;
    private String website;
    private String description;
    private String designation;
    private String department;
    private String officialPhone;

    public CompanyProfileDto() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getOfficialPhone() { return officialPhone; }
    public void setOfficialPhone(String officialPhone) { this.officialPhone = officialPhone; }
}
