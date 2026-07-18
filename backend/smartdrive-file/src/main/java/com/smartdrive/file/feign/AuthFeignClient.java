package com.smartdrive.file.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "smartdrive-auth")
public interface AuthFeignClient {

    @GetMapping("/api/inner/user/{userId}")
    Map<String, Object> getUserInfo(@PathVariable String userId, @RequestHeader("X-User-Id") String callerUserId);

    @PostMapping("/api/inner/users/batch")
    Map<String, Map<String, Object>> batchGetUserInfo(@RequestBody List<String> userIds);

    @PostMapping("/api/inner/user/updateSpace")
    void updateUserSpace(@RequestParam String userId, @RequestParam Long useSpace);

    @PostMapping("/api/inner/user/incrementSpace")
    void incrementUserSpace(@RequestParam String userId, @RequestParam Long delta);

    @GetMapping("/api/inner/department/{deptId}/members")
    List<String> getDepartmentMemberIds(@PathVariable String deptId);

    @PostMapping("/api/inner/department/{deptId}/clearMembers")
    void clearDepartmentMembers(@PathVariable String deptId);
}
