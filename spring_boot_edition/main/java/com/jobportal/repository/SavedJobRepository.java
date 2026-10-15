package com.jobportal.repository;

import com.jobportal.entity.Job;
import com.jobportal.entity.SavedJob;
import com.jobportal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedJobRepository extends JpaRepository<SavedJob, Long> {
    Optional<SavedJob> findByUserAndJob(User user, Job job);
    boolean existsByUserAndJob(User user, Job job);
    List<SavedJob> findByUserOrderBySavedAtDesc(User user);
    void deleteByUserAndJob(User user, Job job);
    long countByUser(User user);
}
