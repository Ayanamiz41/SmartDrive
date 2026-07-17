package com.smartdrive.file.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogEntry {
    private Long id;
    private String userId;
    private String userName;
    private String action;
    private String targetType;
    private String targetId;
    private String targetName;
    private String filePid;
    private Boolean fileDeleted;
    private Boolean fileArchived;
    private String detail;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    private String departmentId;
}
