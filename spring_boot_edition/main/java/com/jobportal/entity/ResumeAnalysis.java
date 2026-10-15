package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resume_analyses")
public class ResumeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private Job targetJob;

    private Integer overallScore; // 0 - 100

    @Column(columnDefinition = "TEXT")
    private String matchedSkills;

    @Column(columnDefinition = "TEXT")
    private String missingSkills;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvementSuggestions;

    @Column(columnDefinition = "TEXT")
    private String formattingFeedback;

    @Column(nullable = false, updatable = false)
    private LocalDateTime analyzedAt = LocalDateTime.now();

    public ResumeAnalysis() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Resume getResume() { return resume; }
    public void setResume(Resume resume) { this.resume = resume; }

    public Job getTargetJob() { return targetJob; }
    public void setTargetJob(Job targetJob) { this.targetJob = targetJob; }

    public Integer getOverallScore() { return overallScore; }
    public void setOverallScore(Integer overallScore) { this.overallScore = overallScore; }

    public String getMatchedSkills() { return matchedSkills; }
    public void setMatchedSkills(String matchedSkills) { this.matchedSkills = matchedSkills; }

    public String getMissingSkills() { return missingSkills; }
    public void setMissingSkills(String missingSkills) { this.missingSkills = missingSkills; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }

    public String getImprovementSuggestions() { return improvementSuggestions; }
    public void setImprovementSuggestions(String improvementSuggestions) { this.improvementSuggestions = improvementSuggestions; }

    public String getFormattingFeedback() { return formattingFeedback; }
    public void setFormattingFeedback(String formattingFeedback) { this.formattingFeedback = formattingFeedback; }

    public LocalDateTime getAnalyzedAt() { return analyzedAt; }
    public void setAnalyzedAt(LocalDateTime analyzedAt) { this.analyzedAt = analyzedAt; }
}
