package com.jobportal.dto;

public class JobSearchCriteria {

    private String keyword;
    private String location;
    private String workArrangement;
    private String employmentType;
    private String experienceLevel;
    private int page = 0;
    private int size = 10;
    private String sortBy = "postedDate";
    private String sortDir = "desc";

    public JobSearchCriteria() {}

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getWorkArrangement() { return workArrangement; }
    public void setWorkArrangement(String workArrangement) { this.workArrangement = workArrangement; }

    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }

    public String getExperienceLevel() { return experienceLevel; }
    public void setExperienceLevel(String experienceLevel) { this.experienceLevel = experienceLevel; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }

    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }

    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { this.sortDir = sortDir; }
}
