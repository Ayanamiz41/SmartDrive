package com.smartdrive.file.controller;

import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.file.entity.Department;
import com.smartdrive.file.service.DepartmentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 部门管理 — 仅管理员可操作
 */
@RestController
@RequestMapping("/admin/department")
public class DepartmentController extends BaseController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /** 获取部门树（所有人可见） */
    @RequestMapping("/list")
    public ResponseVO list() {
        List<Department> tree = departmentService.getAllDepartments();
        return getSuccessResponseVO(tree);
    }

    /** 新建部门（管理员） */
    @PostMapping("/create")
    public ResponseVO create(@RequestBody Department dept) {
        if (!isAdmin()) return getErrorResponse("无权操作");
        departmentService.createDepartment(dept);
        return getSuccessResponseVO(null);
    }

    /** 编辑部门（管理员） */
    @PostMapping("/update")
    public ResponseVO update(@RequestBody Department dept) {
        if (!isAdmin()) return getErrorResponse("无权操作");
        departmentService.updateDepartment(dept);
        return getSuccessResponseVO(null);
    }

    /** 删除部门（管理员） */
    @PostMapping("/delete")
    public ResponseVO delete(@RequestParam String id) {
        if (!isAdmin()) return getErrorResponse("无权操作");
        departmentService.deleteDepartment(id);
        return getSuccessResponseVO(null);
    }

    /** 设置部门主管（管理员） */
    @PostMapping("/setHeadUser")
    public ResponseVO setHeadUser(@RequestParam String id, @RequestParam String headUserId) {
        if (!isAdmin()) return getErrorResponse("无权操作");
        departmentService.setHeadUser(id, headUserId);
        return getSuccessResponseVO(null);
    }

    private ResponseVO getErrorResponse(String msg) {
        ResponseVO vo = new ResponseVO();
        vo.setStatus("error");
        vo.setCode(403);
        vo.setInfo(msg);
        return vo;
    }
}
