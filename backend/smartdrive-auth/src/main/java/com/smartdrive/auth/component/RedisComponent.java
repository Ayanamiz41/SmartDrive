package com.smartdrive.auth.component;

import com.smartdrive.auth.config.AppConfig;
import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.dto.SysSettingDto;
import com.smartdrive.common.dto.UserSpaceDto;
import com.smartdrive.common.utils.RedisUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component("redisComponent")
public class RedisComponent {
    private final RedisUtils<Object> redisUtils;
    private final AppConfig appConfig;
    private final StringRedisTemplate stringRedisTemplate;

    public RedisComponent(RedisUtils<Object> redisUtils, AppConfig appConfig,
                          StringRedisTemplate stringRedisTemplate) {
        this.redisUtils = redisUtils;
        this.appConfig = appConfig;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public SysSettingDto getSysSettingDto() {
        try {
            SysSettingDto dto = (SysSettingDto) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
            if (dto != null) { return dto; }
        } catch (Exception e) {
            // 旧版本序列化数据不兼容，清除后重建
            redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, null);
        }
        SysSettingDto sysSettingDto = new SysSettingDto();
        redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingDto);
        return sysSettingDto;
    }

    public void saveSysSettingDto(SysSettingDto sysSettingDto) {
        if (sysSettingDto == null) { return; }
        redisUtils.set(Constants.REDIS_KEY_SYS_SETTING, sysSettingDto);
    }

    public void saveUserSpace(String userId, UserSpaceDto userSpaceDto) {
        redisUtils.setex(Constants.REDIS_KEY_USER_SPACE + userId, userSpaceDto, Constants.REDIS_KEY_EXPIRES_DAY);
    }

    public UserSpaceDto getUserSpace(String userId) {
        UserSpaceDto userSpaceDto = (UserSpaceDto) redisUtils.get(Constants.REDIS_KEY_USER_SPACE + userId);
        if (userSpaceDto == null) {
            userSpaceDto = new UserSpaceDto();
            userSpaceDto.setUseSpace(0L);
            userSpaceDto.setTotalSpace(getSysSettingDto().getUserInitUseSpace() * Constants.MB);
            saveUserSpace(userId, userSpaceDto);
        }
        return userSpaceDto;
    }

    // === 邮箱验证码 ===

    private static final String EMAIL_CODE_PREFIX = "smartdrive:email:code:";

    public void saveEmailCode(String email, String code) {
        redisUtils.setex(EMAIL_CODE_PREFIX + email, code, Constants.REDIS_KEY_EXPIRES_FIVE_MIN * 3); // 15 min
    }

    public String getEmailCode(String email) {
        return (String) redisUtils.get(EMAIL_CODE_PREFIX + email);
    }

    public void deleteEmailCode(String email) {
        redisUtils.del(EMAIL_CODE_PREFIX + email);
    }

    // === 登录会话（防多地登录互踢） ===

    private static final String SESSION_PREFIX = "smartdrive:session:";

    public void saveSessionId(String userId, String sessionId) {
        // TTL 与 refreshToken 对齐（jwt.refresh-expiration，单位毫秒转秒）
        stringRedisTemplate.opsForValue().set(SESSION_PREFIX + userId, sessionId,
                appConfig.getRefreshExpirationSeconds(), TimeUnit.SECONDS);
    }

    public String getSessionId(String userId) {
        return stringRedisTemplate.opsForValue().get(SESSION_PREFIX + userId);
    }

    // === JWT 黑名单 ===

    public void addToBlacklist(String userId) {
        stringRedisTemplate.opsForValue().set("smartdrive:jwt:blacklist:" + userId,
                String.valueOf(java.time.Instant.now().getEpochSecond()), 1800, TimeUnit.SECONDS);
    }

    /**
     * 返回黑名单时间戳（epoch seconds），若 userId 未在黑名单中返回 null
     */
    public Long getBlacklistTimestamp(String userId) {
        String bannedAt = stringRedisTemplate.opsForValue().get("smartdrive:jwt:blacklist:" + userId);
        return bannedAt != null ? Long.parseLong(bannedAt) : null;
    }
}
