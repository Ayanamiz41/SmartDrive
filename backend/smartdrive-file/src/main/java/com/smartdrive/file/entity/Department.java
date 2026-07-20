package com.smartdrive.file.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Department {
    private String id;
    private String name;
    private String parentId;
    private String headUserId;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    // 非数据库字段：主管昵称（查询时 JOIN 填充）
    private String headUserName;
    // 非数据库字段：下级部门
    private java.util.List<Department> children;
}
