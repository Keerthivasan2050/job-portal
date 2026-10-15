package com.jobportal.service;

import com.jobportal.dto.MatchResultDto;
import com.jobportal.entity.Job;
import com.jobportal.entity.JobSeekerProfile;
import com.jobportal.entity.User;

import java.util.List;

public interface MatchingEngineService {
    List<MatchResultDto> getRecommendedJobsForSeeker(User seeker);
    MatchResultDto calculateJobMatch(JobSeekerProfile profile, Job job);
}
