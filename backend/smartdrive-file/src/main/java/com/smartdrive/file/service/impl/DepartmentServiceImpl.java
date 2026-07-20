package com.smartdrive.file.service.impl;

import com.smartdrive.file.entity.Department;
import com.smartdrive.file.feign.AuthFeignClient;
import com.smartdrive.file.mapper.DepartmentMapper;
import com.smartdrive.file.service.DepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final AuthFeignClient authFeignClient;

    public DepartmentServiceImpl(DepartmentMapper departmentMapper, AuthFeignClient authFeignClient) {
        this.departmentMapper = departmentMapper;
        this.authFeignClient = authFeignClient;
    }

    @Override
    public List<Department> getAllDepartments() {
        List<Department> depts = departmentMapper.selectAll();
        enrichHeadNames(depts);
        return depts;
    }

    @Override
    public Department getById(String id) {
        Department dept = departmentMapper.selectById(id);
        if (dept != null) {
            enrichHeadNames(List.of(dept));
        }
        return dept;
    }

    /**
     * 通过 Feign 批量获取部门主管昵称
     */
    private void enrichHeadNames(List<Department> depts) {
        if (depts == null || depts.isEmpty()) return;
        Set<String> userIds = new HashSet<>();
        for (Department d : depts) {
            if (d.getHeadUserId() != null) userIds.add(d.getHeadUserId());
        }
        if (userIds.isEmpty()) return;
        try {
            Map<String, Map<String, Object>> userMap = authFeignClient.batchGetUserInfo(new ArrayList<>(userIds));
            for (Department d : depts) {
                Map<String, Object> u = userMap.get(d.getHeadUserId());
                if (u != null) d.setHeadUserName((String) u.get("nickName"));
            }
        } catch (Exception e) {
            // 昵称获取失败不影响主流程
        }
    }

    @Override
    public void createDepartment(Department dept) {
        if (dept.getId() == null || dept.getId().isEmpty()) {
            dept.setId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        }
        checkNameUnique(dept.getName(), null);
        departmentMapper.insert(dept);
    }

    @Override
    public void updateDepartment(Department dept) {
        checkNameUnique(dept.getName(), dept.getId());
        departmentMapper.update(dept);
    }

    private void checkNameUnique(String name, String excludeId) {
        Department exist = departmentMapper.selectByName(name, excludeId);
        if (exist != null) {
            throw new com.smartdrive.common.exception.BusinessException("部门名称已存在");
        }
    }

    @Override
    @Transactional
    public void deleteDepartment(String id) {
        try { authFeignClient.clearDepartmentMembers(id); } catch (Exception ignored) {}
        departmentMapper.setHeadUser(id, null);
        departmentMapper.deleteById(id);
    }

    @Override
    public void setHeadUser(String id, String headUserId) {
        departmentMapper.setHeadUser(id, headUserId);
    }

    @Override
    public boolean isDeptHead(String departmentId, String userId) {
        String headId = departmentMapper.getHeadUserId(departmentId);
        return headId != null && headId.equals(userId);
    }

    @Override
    public List<String> getMemberUserIds(String departmentId) {
        try {
            return authFeignClient.getDepartmentMemberIds(departmentId);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<String> getUserDepartmentIds(String userId) {
        return Collections.emptyList();
    }

    @Override
    public String getUserDepartmentId(String userId) {
        return null;
    }
}
