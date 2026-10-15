package com.jobportal.service;

import com.jobportal.entity.Notification;
import com.jobportal.entity.User;

import java.util.List;

public interface NotificationService {
    void sendNotification(User recipient, String title, String message, String type, String link);
    List<Notification> getNotificationsForUser(User user);
    void markAsRead(Long notificationId, User user);
    void markAllAsRead(User user);
    long getUnreadCount(User user);
}
