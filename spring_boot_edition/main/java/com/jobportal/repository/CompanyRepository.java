package com.jobportal.repository;

import com.jobportal.entity.Company;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findByNameIgnoreCase(String name);
    List<Company> findByVerifiedTrue();
    Page<Company> findAll(Pageable pageable);
    long countByVerifiedTrue();
}
