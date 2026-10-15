package com.jobportal.repository;

import com.jobportal.entity.Application;
import com.jobportal.entity.ApplicationStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {
    List<ApplicationStatusHistory> findByApplicationOrderByTimestampAsc(Application application);
}
