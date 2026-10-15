package com.jobportal.service;

import com.jobportal.dto.InterviewScheduleDto;
import com.jobportal.entity.Interview;
import com.jobportal.entity.User;

import java.util.List;

public interface InterviewService {
    Interview scheduleInterview(InterviewScheduleDto dto, User recruiter);
    List<Interview> getInterviewsForCandidate(User candidate);
    List<Interview> getInterviewsForRecruiter(User recruiter);
    Interview getInterviewById(Long id);
    void updateInterviewStatus(Long interviewId, String status, User user);
    void cancelInterview(Long interviewId, User user);
}
