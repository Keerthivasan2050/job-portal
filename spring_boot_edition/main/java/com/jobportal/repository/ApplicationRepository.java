package com.jobportal.repository;

import com.jobportal.entity.Application;
import com.jobportal.entity.Job;
import com.jobportal.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application> findByJobAndCandidate(Job job, User candidate);
    boolean existsByJobAndCandidate(Job job, User candidate);
    List<Application> findByCandidateOrderByAppliedAtDesc(User candidate);
    Page<Application> findByCandidateOrderByAppliedAtDesc(User candidate, Pageable pageable);
    List<Application> findByJobOrderByAppliedAtDesc(Job job);
    Page<Application> findByJobOrderByAppliedAtDesc(Job job, Pageable pageable);
    long countByJob(Job job);
    long countByStatus(String status);
    long countByCandidate(User candidate);
    List<Application> findTop5ByOrderByAppliedAtDesc();
}
