package com.smartdrive.auth.controller;

import com.smartdrive.auth.component.RedisComponent;
import com.smartdrive.auth.service.UserInfoService;
import com.smartdrive.auth.entity.query.UserInfoQuery;
import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.dto.SysSettingDto;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class AdminController extends BaseController {

    private final UserInfoService userInfoService;
    private final RedisComponent redisComponent;

    public AdminController(UserInfoService userInfoService, RedisComponent redisComponent) {
        this.userInfoService = userInfoService;
        this.redisComponent = redisComponent;
    }

    // ===== 系统设置 — 完整保留 =====
    @RequestMapping("/admin/getSysSettings")
    public ResponseVO getSysSettings() {
        return getSuccessResponseVO(redisComponent.getSysSettingDto());
    }

    @PostMapping("/admin/saveSysSettings")
    public ResponseVO saveSysSettings(@RequestParam String registerEmailTitle,
                                       @RequestParam String registerEmailContent,
                                       @RequestParam Integer userInitUseSpace) {
        SysSettingDto sysSettingDto = new SysSettingDto();
        sysSettingDto.setRegisterEmailTitle(registerEmailTitle);
        sysSettingDto.setRegisterEmailContent(registerEmailContent);
        sysSettingDto.setUserInitUseSpace(userInitUseSpace);
        redisComponent.saveSysSettingDto(sysSettingDto);
        return getSuccessResponseVO(null);
    }

    // ===== 用户列表 =====
    @RequestMapping("/admin/loadUserList")
    public ResponseVO loadUserList(UserInfoQuery userInfoQuery) {
        userInfoQuery.setOrderBy("join_time desc");
        PaginationResultVO result = userInfoService.findListByPage(userInfoQuery);
        return getSuccessResponseVO(convert2PaginationVO(result, com.smartdrive.common.vo.UserInfoVO.class));
    }

    // ===== 更新用户状态 =====
    @PostMapping("/admin/updateUserStatus")
    public ResponseVO updateUserStatus(@RequestParam String userId, @RequestParam Integer status) {
        userInfoService.updateUserStatus(userId, status);
        return getSuccessResponseVO(null);
    }

    // ===== 更新用户空间 =====
    @PostMapping("/admin/updateUserSpace")
    public ResponseVO updateUserSpace(@RequestParam String userId, @RequestParam Long changeSpace) {
        userInfoService.updateUserSpace(userId, changeSpace);
        return getSuccessResponseVO(null);
    }

    // ===== 分配员工到部门 =====
    @PostMapping("/admin/assignDepartment")
    public ResponseVO assignDepartment(@RequestParam String userId, @RequestParam(required = false) String departmentId) {
        userInfoService.assignDepartment(userId, departmentId);
        return getSuccessResponseVO(null);
    }

    // ===== 获取部门成员 =====
    @RequestMapping("/admin/departmentMembers")
    public ResponseVO departmentMembers(@RequestParam String departmentId) {
        return getSuccessResponseVO(userInfoService.getDepartmentMembers(departmentId));
    }

}
