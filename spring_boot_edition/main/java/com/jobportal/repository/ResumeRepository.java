package com.jobportal.repository;

import com.jobportal.entity.Resume;
import com.jobportal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeRepository extends JpaRepository<Resume, Long> {
    List<Resume> findByUserOrderByUploadedAtDesc(User user);
    Optional<Resume> findTopByUserOrderByUploadedAtDesc(User user);
    long countByUser(User user);
}
