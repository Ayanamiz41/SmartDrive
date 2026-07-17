package com.smartdrive.file.service.impl;

import com.smartdrive.file.entity.AuditLogEntry;
import com.smartdrive.file.mapper.AuditLogMapper;
import com.smartdrive.file.service.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    public AuditLogServiceImpl(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    public void log(String userId, String userName, String action, String targetType,
                    String targetId, String targetName) {
        log(userId, userName, action, targetType, targetId, targetName, null);
    }

    @Override
    public void log(String userId, String userName, String action, String targetType,
                    String targetId, String targetName, String departmentId) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setUserId(userId);
        entry.setUserName(userName);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setTargetName(targetName);
        entry.setDepartmentId(departmentId);
        auditLogMapper.insert(entry);
    }

    @Override
    public List<AuditLogEntry> listAuditLogs(String userId, String action,
                                              LocalDateTime startTime, LocalDateTime endTime,
                                              List<String> deptUserIds, String departmentId,
                                              String keyword, String orderBy, int offset, int limit) {
        return auditLogMapper.selectList(userId, action, startTime, endTime, deptUserIds, departmentId, keyword, orderBy, offset, limit);
    }

    @Override
    public int countAuditLogs(String userId, String action,
                               LocalDateTime startTime, LocalDateTime endTime,
                               List<String> deptUserIds, String departmentId,
                               String keyword) {
        return auditLogMapper.selectCount(userId, action, startTime, endTime, deptUserIds, departmentId, keyword);
    }
}
