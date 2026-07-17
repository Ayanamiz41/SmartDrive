package com.smartdrive.file.service;

import com.smartdrive.file.entity.AuditLogEntry;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogService {

    void log(String userId, String userName, String action, String targetType,
             String targetId, String targetName);

    void log(String userId, String userName, String action, String targetType,
             String targetId, String targetName, String departmentId);

    List<AuditLogEntry> listAuditLogs(String userId, String action,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       List<String> deptUserIds, String departmentId,
                                       String keyword, String orderBy, int offset, int limit);

    int countAuditLogs(String userId, String action,
                       LocalDateTime startTime, LocalDateTime endTime,
                       List<String> deptUserIds, String departmentId,
                       String keyword);
}
