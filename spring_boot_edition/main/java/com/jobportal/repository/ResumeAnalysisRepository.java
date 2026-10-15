package com.jobportal.repository;

import com.jobportal.entity.Job;
import com.jobportal.entity.Resume;
import com.jobportal.entity.ResumeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {
    List<ResumeAnalysis> findByResumeOrderByAnalyzedAtDesc(Resume resume);
    Optional<ResumeAnalysis> findTopByResumeOrderByAnalyzedAtDesc(Resume resume);
    Optional<ResumeAnalysis> findTopByResumeAndTargetJobOrderByAnalyzedAtDesc(Resume resume, Job job);
}
