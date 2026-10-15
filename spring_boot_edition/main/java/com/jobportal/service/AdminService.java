package com.jobportal.service;

import com.jobportal.dto.AdminDashboardStatsDto;
import com.jobportal.entity.AuditLog;

import java.util.List;

public interface AdminService {
    AdminDashboardStatsDto getDashboardStats();
    List<AuditLog> getRecentAuditLogs();
}
