package com.jobportal.service.impl;

import com.jobportal.dto.CompanyProfileDto;
import com.jobportal.dto.JobSeekerProfileDto;
import com.jobportal.dto.UserRegistrationDto;
import com.jobportal.entity.*;
import com.jobportal.exception.ResourceNotFoundException;
import com.jobportal.repository.CompanyRepository;
import com.jobportal.repository.JobSeekerProfileRepository;
import com.jobportal.repository.RecruiterProfileRepository;
import com.jobportal.repository.UserRepository;
import com.jobportal.service.AuditLogService;
import com.jobportal.service.UserService;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final JobSeekerProfileRepository jobSeekerProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserServiceImpl(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            JobSeekerProfileRepository jobSeekerProfileRepository,
            RecruiterProfileRepository recruiterProfileRepository,
            @Lazy PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.jobSeekerProfileRepository = jobSeekerProfileRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public User registerJobSeeker(UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("An account with email " + dto.getEmail() + " already exists.");
        }

        User user = new User(
                dto.getEmail(),
                passwordEncoder.encode(dto.getPassword()),
                dto.getFullName(),
                dto.getPhone(),
                Role.ROLE_JOB_SEEKER
        );
        User savedUser = userRepository.save(user);

        JobSeekerProfile profile = new JobSeekerProfile(savedUser);
        profile.setHeadline("Aspiring Professional");
        jobSeekerProfileRepository.save(profile);

        auditLogService.logAction("USER_REGISTRATION", savedUser.getEmail(), "User", savedUser.getId().toString(), "Job Seeker Registered", "system");
        return savedUser;
    }

    @Override
    @Transactional
    public User registerRecruiter(UserRegistrationDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("An account with email " + dto.getEmail() + " already exists.");
        }

        User user = new User(
                dto.getEmail(),
                passwordEncoder.encode(dto.getPassword()),
                dto.getFullName(),
                dto.getPhone(),
                Role.ROLE_RECRUITER
        );
        User savedUser = userRepository.save(user);

        String companyName = (dto.getCompanyName() != null && !dto.getCompanyName().isBlank()) 
                ? dto.getCompanyName().trim() : "Independent Organization";

        Company company = companyRepository.findByNameIgnoreCase(companyName)
                .orElseGet(() -> companyRepository.save(new Company(companyName, "Technology", "Global", "", "Leading Innovation")));

        RecruiterProfile recruiterProfile = new RecruiterProfile(savedUser, company, "Talent Acquisition Manager");
        recruiterProfileRepository.save(recruiterProfile);

        auditLogService.logAction("USER_REGISTRATION", savedUser.getEmail(), "User", savedUser.getId().toString(), "Recruiter Registered with " + companyName, "system");
        return savedUser;
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    @Override
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @Transactional
    public void updateJobSeekerProfile(User user, JobSeekerProfileDto dto) {
        user.setFullName(dto.getFullName());
        if (dto.getPhone() != null) user.setPhone(dto.getPhone());
        userRepository.save(user);

        JobSeekerProfile profile = jobSeekerProfileRepository.findByUser(user)
                .orElseGet(() -> new JobSeekerProfile(user));

        profile.setHeadline(dto.getHeadline());
        profile.setBio(dto.getBio());
        profile.setSkills(dto.getSkills());
        profile.setQualification(dto.getQualification());
        profile.setExperienceYears(dto.getExperienceYears() != null ? dto.getExperienceYears() : 0.0);
        profile.setPreferredTitle(dto.getPreferredTitle());
        profile.setPreferredLocation(dto.getPreferredLocation());
        profile.setExpectedSalary(dto.getExpectedSalary());
        if (dto.getWorkArrangementPreference() != null) {
            profile.setWorkArrangementPreference(dto.getWorkArrangementPreference());
        }
        if (dto.getEmploymentTypePreference() != null) {
            profile.setEmploymentTypePreference(dto.getEmploymentTypePreference());
        }
        jobSeekerProfileRepository.save(profile);
    }

    @Override
    @Transactional
    public void updateCompanyProfile(User user, CompanyProfileDto dto) {
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter profile not found for user: " + user.getEmail()));

        Company company = recruiterProfile.getCompany();
        if (company == null) {
            company = new Company();
            company = companyRepository.save(company);
            recruiterProfile.setCompany(company);
        }

        company.setName(dto.getName());
        company.setIndustry(dto.getIndustry());
        company.setLocation(dto.getLocation());
        company.setWebsite(dto.getWebsite());
        company.setDescription(dto.getDescription());
        companyRepository.save(company);

        recruiterProfile.setDesignation(dto.getDesignation());
        recruiterProfile.setDepartment(dto.getDepartment());
        recruiterProfile.setOfficialPhone(dto.getOfficialPhone());
        recruiterProfileRepository.save(recruiterProfile);
    }

    @Override
    @Transactional
    public String createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        user.setResetPasswordToken(token);
        userRepository.save(user);
        return token;
    }

    @Override
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        if (token == null || token.isBlank()) return false;
        User user = userRepository.findByResetPasswordToken(token).orElse(null);
        if (user == null) return false;

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        userRepository.save(user);
        return true;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public Page<User> getAllUsersPaged(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Override
    @Transactional
    public void toggleUserStatus(Long userId) {
        User user = findById(userId);
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        auditLogService.logAction("USER_STATUS_TOGGLE", "ADMIN", "User", userId.toString(), "Status changed to: " + user.isEnabled(), "system");
    }
}
