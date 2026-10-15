package com.jobportal.repository;

import com.jobportal.entity.Application;
import com.jobportal.entity.Interview;
import com.jobportal.entity.Job;
import com.jobportal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByCandidateOrderByInterviewDateDesc(User candidate);
    List<Interview> findByRecruiterOrderByInterviewDateDesc(User recruiter);
    List<Interview> findByJob(Job job);
    Optional<Interview> findByApplication(Application application);
    long countByRecruiter(User recruiter);
    long countByCandidate(User candidate);
}
