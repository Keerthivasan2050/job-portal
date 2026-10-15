package com.jobportal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class InterviewScheduleDto {

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    @NotBlank(message = "Interview date and time is required")
    private String interviewDate; // Form: YYYY-MM-DDTHH:mm

    private String interviewType = "ONLINE_VIDEO";
    private String meetingLink;
    private String location;
    private String notes;

    public InterviewScheduleDto() {}

    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }

    public String getInterviewDate() { return interviewDate; }
    public void setInterviewDate(String interviewDate) { this.interviewDate = interviewDate; }

    public String getInterviewType() { return interviewType; }
    public void setInterviewType(String interviewType) { this.interviewType = interviewType; }

    public String getMeetingLink() { return meetingLink; }
    public void setMeetingLink(String meetingLink) { this.meetingLink = meetingLink; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
