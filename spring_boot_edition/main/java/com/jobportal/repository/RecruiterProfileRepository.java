package com.jobportal.repository;

import com.jobportal.entity.Company;
import com.jobportal.entity.RecruiterProfile;
import com.jobportal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecruiterProfileRepository extends JpaRepository<RecruiterProfile, Long> {
    Optional<RecruiterProfile> findByUser(User user);
    Optional<RecruiterProfile> findByUserId(Long userId);
    List<RecruiterProfile> findByCompany(Company company);
}
