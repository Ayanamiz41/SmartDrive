package com.smartdrive.common.dto;

import lombok.Data;

@Data
public class LoginResultDto {
    private String accessToken;
    private String refreshToken;
    private SessionWebUserDto userInfo;
}
