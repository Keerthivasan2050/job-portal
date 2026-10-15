package com.jobportal.repository;

import com.jobportal.entity.Notification;
import com.jobportal.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient);
    List<Notification> findTop10ByRecipientOrderByCreatedAtDesc(User recipient);
    long countByRecipientAndIsReadFalse(User recipient);
}
