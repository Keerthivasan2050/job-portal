package com.jobportal.service.impl;

import com.jobportal.entity.AuditLog;
import com.jobportal.repository.AuditLogRepository;
import com.jobportal.service.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogServiceImpl(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional
    public void logAction(String action, String performedBy, String entityType, String entityId, String details, String ipAddress) {
        AuditLog log = new AuditLog(action, performedBy, entityType, entityId, details, ipAddress);
        auditLogRepository.save(log);
    }
}
