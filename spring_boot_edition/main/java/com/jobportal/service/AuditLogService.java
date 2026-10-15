package com.jobportal.service;

public interface AuditLogService {
    void logAction(String action, String performedBy, String entityType, String entityId, String details, String ipAddress);
}
