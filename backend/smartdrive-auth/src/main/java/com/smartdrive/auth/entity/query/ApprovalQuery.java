package com.smartdrive.auth.entity.query;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ApprovalQuery {
    private Integer status;
    private String applicantId;
    private String approverId;
    private String departmentId;
    private String excludeApplicantId;  // 主管排除自己
    private LocalDateTime createTimeStart;
    private LocalDateTime createTimeEnd;
    private LocalDateTime handleTimeStart;
    private LocalDateTime handleTimeEnd;

    private Integer pageNo;
    private Integer pageSize;
    private String orderBy;
}
