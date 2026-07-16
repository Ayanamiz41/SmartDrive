package com.smartdrive.auth.service;

public interface EmailCodeService {
    void sendEmailCode(String email, Integer type);
    void checkCode(String email, String code);
}
