package com.smartdrive.auth.controller;

import com.smartdrive.auth.entity.UserInfo;
import com.smartdrive.auth.service.UserInfoService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内部接口，供其他微服务通过 Feign 调用，不走 Gateway 鉴权
 */
@RestController
@RequestMapping("/inner")
public class InnerAuthController {

    private final UserInfoService userInfoService;

    public InnerAuthController(UserInfoService userInfoService) {
        this.userInfoService = userInfoService;
    }

    /** 获取用户基本信息（昵称、头像） */
    @GetMapping("/user/{userId}")
    public Map<String, Object> getUserInfo(@PathVariable String userId) {
        UserInfo userInfo = userInfoService.getUserInfoByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        if (userInfo != null) {
            result.put("userId", userInfo.getUserId());
            result.put("nickName", userInfo.getNickName());
            result.put("avatar", userInfo.getAvatar());
        }
        return result;
    }

    /** 批量获取用户基本信息 */
    @PostMapping("/users/batch")
    public Map<String, Map<String, Object>> batchGetUserInfo(@RequestBody List<String> userIds) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (String userId : userIds) {
            UserInfo userInfo = userInfoService.getUserInfoByUserId(userId);
            if (userInfo != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("userId", userInfo.getUserId());
                info.put("nickName", userInfo.getNickName());
                info.put("avatar", userInfo.getAvatar());
                result.put(userId, info);
            }
        }
        return result;
    }

    /** file服务内部调用：更新用户已用空间（绝对值） */
    @PostMapping("/user/updateSpace")
    public void updateUserSpace(@RequestParam String userId, @RequestParam Long useSpace) {
        userInfoService.updateUseSpace(userId, useSpace);
    }

    /** file服务内部调用：增量更新用户已用空间（原子 += ） */
    @PostMapping("/user/incrementSpace")
    public void incrementUserSpace(@RequestParam String userId, @RequestParam Long delta) {
        userInfoService.incrementUseSpace(userId, delta);
    }

    /** file服务内部调用：获取部门成员ID列表 */
    @GetMapping("/department/{deptId}/members")
    public List<String> getDepartmentMemberIds(@PathVariable String deptId) {
        return userInfoService.getDepartmentMemberIds(deptId);
    }

    /** file服务内部调用：清除部门下所有成员的部门归属 */
    @PostMapping("/department/{deptId}/clearMembers")
    public void clearDepartmentMembers(@PathVariable String deptId) {
        userInfoService.clearDepartmentMembers(deptId);
    }
}
