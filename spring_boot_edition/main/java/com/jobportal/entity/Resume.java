package com.jobportal.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 50)
    private String fileType; // PDF, DOCX

    @Column(nullable = false, length = 500)
    private String filePath;

    private Long fileSize;

    @Column(columnDefinition = "LONGTEXT")
    private String rawText;

    @Column(columnDefinition = "TEXT")
    private String extractedSkills;

    @Column(columnDefinition = "TEXT")
    private String extractedEducation;

    @Column(columnDefinition = "TEXT")
    private String extractedExperience;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt = LocalDateTime.now();

    public Resume() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public String getExtractedSkills() { return extractedSkills; }
    public void setExtractedSkills(String extractedSkills) { this.extractedSkills = extractedSkills; }

    public String getExtractedEducation() { return extractedEducation; }
    public void setExtractedEducation(String extractedEducation) { this.extractedEducation = extractedEducation; }

    public String getExtractedExperience() { return extractedExperience; }
    public void setExtractedExperience(String extractedExperience) { this.extractedExperience = extractedExperience; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
