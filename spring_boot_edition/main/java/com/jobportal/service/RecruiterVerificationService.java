package com.jobportal.service;

import com.jobportal.dto.RecruiterVerificationDto;
import com.jobportal.entity.RecruiterVerification;
import com.jobportal.entity.User;

import java.util.List;

public interface RecruiterVerificationService {
    RecruiterVerification submitVerification(User recruiter, RecruiterVerificationDto dto);
    RecruiterVerification getVerificationForRecruiter(User recruiter);
    List<RecruiterVerification> getPendingVerifications();
    List<RecruiterVerification> getAllVerifications();
    void reviewVerification(Long verificationId, String status, String adminNotes, User admin);
}
