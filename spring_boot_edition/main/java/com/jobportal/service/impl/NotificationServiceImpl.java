package com.jobportal.service.impl;

import com.jobportal.entity.Notification;
import com.jobportal.entity.User;
import com.jobportal.exception.UnauthorizedAccessException;
import com.jobportal.repository.NotificationRepository;
import com.jobportal.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional
    public void sendNotification(User recipient, String title, String message, String type, String link) {
        if (recipient == null) return;
        Notification notification = new Notification(recipient, title, message, type, link);
        notificationRepository.save(notification);
    }

    @Override
    public List<Notification> getNotificationsForUser(User user) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, User user) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification != null) {
            if (!notification.getRecipient().getId().equals(user.getId())) {
                throw new UnauthorizedAccessException("Cannot modify another user's notifications.");
            }
            notification.setRead(true);
            notificationRepository.save(notification);
        }
    }

    @Override
    @Transactional
    public void markAllAsRead(User user) {
        List<Notification> list = notificationRepository.findByRecipientOrderByCreatedAtDesc(user);
        for (Notification n : list) {
            if (!n.isRead()) {
                n.setRead(true);
            }
        }
        notificationRepository.saveAll(list);
    }

    @Override
    public long getUnreadCount(User user) {
        return notificationRepository.countByRecipientAndIsReadFalse(user);
    }
}
