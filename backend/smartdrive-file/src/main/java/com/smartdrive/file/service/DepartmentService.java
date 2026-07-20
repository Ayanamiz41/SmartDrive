package com.smartdrive.file.service;

import com.smartdrive.file.entity.Department;

import java.util.List;

public interface DepartmentService {

    List<Department> getAllDepartments();

    Department getById(String id);

    void createDepartment(Department dept);

    void updateDepartment(Department dept);

    void deleteDepartment(String id);

    void setHeadUser(String id, String headUserId);

    boolean isDeptHead(String departmentId, String userId);

    List<String> getMemberUserIds(String departmentId);

    List<String> getUserDepartmentIds(String userId);

    String getUserDepartmentId(String userId);
}
