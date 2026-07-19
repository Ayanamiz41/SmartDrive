package com.smartdrive.share.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "smartdrive-auth")
public interface AuthFeignClient {

    @GetMapping("/api/inner/user/{userId}")
    Map<String, Object> getUserInfo(@PathVariable String userId);
}
