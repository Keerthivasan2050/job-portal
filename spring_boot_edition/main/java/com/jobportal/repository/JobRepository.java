package com.jobportal.repository;

import com.jobportal.entity.Company;
import com.jobportal.entity.Job;
import com.jobportal.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    Page<Job> findByStatus(String status, Pageable pageable);
    List<Job> findByStatus(String status);
    List<Job> findByRecruiterOrderByPostedDateDesc(User recruiter);
    Page<Job> findByRecruiterOrderByPostedDateDesc(User recruiter, Pageable pageable);
    List<Job> findByCompany(Company company);
    long countByStatus(String status);

    @Query("SELECT j FROM Job j WHERE j.status = 'OPEN' AND (" +
           "LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.company.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.requiredSkills) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Job> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE j.status = 'OPEN' AND " +
           "(:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(j.company.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(j.requiredSkills) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND " +
           "(:workArrangement IS NULL OR j.workArrangement = :workArrangement) AND " +
           "(:employmentType IS NULL OR j.employmentType = :employmentType) AND " +
           "(:experienceLevel IS NULL OR j.experienceLevel = :experienceLevel)")
    Page<Job> searchJobsAdvanced(
            @Param("keyword") String keyword,
            @Param("location") String location,
            @Param("workArrangement") String workArrangement,
            @Param("employmentType") String employmentType,
            @Param("experienceLevel") String experienceLevel,
            Pageable pageable);

    List<Job> findTop6ByStatusOrderByPostedDateDesc(String status);
}
