package com.jobportal.dto;

import java.util.List;

public class MatchResultDto {

    private Long jobId;
    private String jobTitle;
    private String companyName;
    private String location;
    private String workArrangement;
    private String employmentType;
    private int matchScore; // 0 - 100 %
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private String matchExplanation;

    public MatchResultDto() {}

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getWorkArrangement() { return workArrangement; }
    public void setWorkArrangement(String workArrangement) { this.workArrangement = workArrangement; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }

    public List<String> getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(List<String> matchedSkills) { this.matchedSkills = matchedSkills; }

    public List<String> getMissingSkills() { return missingSkills; }
    public void setMissingSkills(List<String> missingSkills) { this.missingSkills = missingSkills; }

    public String getMatchExplanation() { return matchExplanation; }
    public void setMatchExplanation(String matchExplanation) { this.matchExplanation = matchExplanation; }
}
