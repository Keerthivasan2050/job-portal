package com.jobportal.service;

import com.jobportal.dto.CompanyProfileDto;
import com.jobportal.dto.JobSeekerProfileDto;
import com.jobportal.dto.UserRegistrationDto;
import com.jobportal.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface UserService {
    User registerJobSeeker(UserRegistrationDto dto);
    User registerRecruiter(UserRegistrationDto dto);
    User findByEmail(String email);
    User findById(Long id);
    void updateJobSeekerProfile(User user, JobSeekerProfileDto dto);
    void updateCompanyProfile(User user, CompanyProfileDto dto);
    String createPasswordResetToken(String email);
    boolean resetPassword(String token, String newPassword);
    List<User> getAllUsers();
    Page<User> getAllUsersPaged(Pageable pageable);
    void toggleUserStatus(Long userId);
}
