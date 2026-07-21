package com.smartdrive.auth.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalVO {
    private String id;
    private String applicantId;
    private String applicantName;
    private String departmentId;
    private String departmentName;
    private String approverId;
    private String approverName;
    private String fileId;
    private String fileName;
    private String fileDeptId;
    private String filePid;
    private Boolean fileDeleted;
    private Boolean fileArchived;
    private String content;
    private Integer status;
    private String comment;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
