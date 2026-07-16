package com.smartdrive.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"com.smartdrive.auth", "com.smartdrive.common"})
@EnableTransactionManagement
@MapperScan(basePackages = {"com.smartdrive.auth.mapper"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.smartdrive.auth.feign"})
@EnableScheduling
@EnableAsync
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
