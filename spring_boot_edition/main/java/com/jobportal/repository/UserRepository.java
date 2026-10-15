package com.jobportal.repository;

import com.jobportal.entity.Role;
import com.jobportal.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByResetPasswordToken(String resetPasswordToken);
    long countByRole(Role role);
    List<User> findByRole(Role role);
    Page<User> findAll(Pageable pageable);
}
