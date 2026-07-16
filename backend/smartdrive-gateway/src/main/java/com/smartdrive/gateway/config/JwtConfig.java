package com.smartdrive.gateway.config;

import com.smartdrive.gateway.util.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(secret, expiration, refreshExpiration);
    }
}
