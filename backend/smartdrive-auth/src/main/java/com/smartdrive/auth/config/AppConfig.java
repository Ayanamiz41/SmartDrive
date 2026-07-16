package com.smartdrive.auth.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("appConfig")
@Data
public class AppConfig {
    @Value("${spring.mail.username:}")
    private String sendUserName;

    @Value("${project.folder}")
    private String projectFolder;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    public long getRefreshExpirationSeconds() {
        return refreshExpirationMs / 1000;
    }
}
