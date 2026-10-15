package com.jobportal.repository;

import com.jobportal.entity.RecruiterVerification;
import com.jobportal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecruiterVerificationRepository extends JpaRepository<RecruiterVerification, Long> {
    List<RecruiterVerification> findByStatus(String status);
    List<RecruiterVerification> findAllByOrderBySubmittedAtDesc();
    Optional<RecruiterVerification> findTopByRecruiterOrderBySubmittedAtDesc(User recruiter);
    long countByStatus(String status);
}
