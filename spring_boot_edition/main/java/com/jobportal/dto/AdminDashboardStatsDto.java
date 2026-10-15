package com.jobportal.dto;

import java.util.Map;

public class AdminDashboardStatsDto {

    private long totalUsers;
    private long totalJobSeekers;
    private long totalRecruiters;
    private long pendingVerifications;
    private long verifiedCompanies;
    private long activeJobs;
    private long totalApplications;
    private long pendingReports;
    private Map<String, Long> applicationStatusDistribution;

    public AdminDashboardStatsDto() {}

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getTotalJobSeekers() { return totalJobSeekers; }
    public void setTotalJobSeekers(long totalJobSeekers) { this.totalJobSeekers = totalJobSeekers; }

    public long getTotalRecruiters() { return totalRecruiters; }
    public void setTotalRecruiters(long totalRecruiters) { this.totalRecruiters = totalRecruiters; }

    public long getPendingVerifications() { return pendingVerifications; }
    public void setPendingVerifications(long pendingVerifications) { this.pendingVerifications = pendingVerifications; }

    public long getVerifiedCompanies() { return verifiedCompanies; }
    public void setVerifiedCompanies(long verifiedCompanies) { this.verifiedCompanies = verifiedCompanies; }

    public long getActiveJobs() { return activeJobs; }
    public void setActiveJobs(long activeJobs) { this.activeJobs = activeJobs; }

    public long getTotalApplications() { return totalApplications; }
    public void setTotalApplications(long totalApplications) { this.totalApplications = totalApplications; }

    public long getPendingReports() { return pendingReports; }
    public void setPendingReports(long pendingReports) { this.pendingReports = pendingReports; }

    public Map<String, Long> getApplicationStatusDistribution() { return applicationStatusDistribution; }
    public void setApplicationStatusDistribution(Map<String, Long> applicationStatusDistribution) { this.applicationStatusDistribution = applicationStatusDistribution; }
}
