package com.smartdrive.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("approval_request")
public class ApprovalRequest {
    @TableId
    private String id;
    private String applicantId;
    private String departmentId;
    private String approverId;
    private String fileId;
    private String fileName;
    private String content;
    private Integer status;
    private String comment;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}
