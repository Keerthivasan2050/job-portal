package com.jobportal.service;

import com.jobportal.entity.Resume;
import com.jobportal.entity.ResumeAnalysis;
import com.jobportal.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ResumeService {
    Resume uploadAndProcessResume(MultipartFile file, User user) throws IOException;
    List<Resume> getResumesByUser(User user);
    Resume getResumeById(Long resumeId, User user);
    ResumeAnalysis analyzeResumeForJob(Long resumeId, Long jobId, User user);
    ResumeAnalysis analyzeResumeGeneral(Long resumeId, User user);
    void deleteResume(Long resumeId, User user);
}
