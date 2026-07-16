package com.smartdrive.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component("redisUtils")
public class RedisUtils<V> {
    private final RedisTemplate<String, V> redisTemplate;
    private static final Logger logger = LoggerFactory.getLogger(RedisUtils.class);

    public RedisUtils(RedisTemplate<String, V> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public V get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    public boolean set(String key, V value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            return true;
        } catch (Exception e) {
            logger.error("设置redisKey:{},value:{}失败", key, value, e);
            return false;
        }
    }

    public boolean setex(String key, V value, long time) {
        try {
            if (time > 0) {
                redisTemplate.opsForValue().set(key, value, time, TimeUnit.SECONDS);
            } else {
                set(key, value);
            }
            return true;
        } catch (Exception e) {
            logger.error("设置redisKey:{},value:{}失败", key, value, e);
            return false;
        }
    }

    public boolean del(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            logger.error("删除redisKey:{}失败", key, e);
            return false;
        }
    }

    public boolean sadd(String key, V value) {
        try {
            Long result = redisTemplate.opsForSet().add(key, value);
            return result != null && result > 0;
        } catch (Exception e) {
            logger.error("sadd redisKey:{}失败", key, e);
            return false;
        }
    }

    public boolean srem(String key, V value) {
        try {
            Long result = redisTemplate.opsForSet().remove(key, value);
            return result != null && result > 0;
        } catch (Exception e) {
            logger.error("srem redisKey:{}失败", key, e);
            return false;
        }
    }

    public java.util.Set<V> smembers(String key) {
        try {
            java.util.Set<V> result = redisTemplate.opsForSet().members(key);
            return result != null ? result : java.util.Set.of();
        } catch (Exception e) {
            logger.error("smembers redisKey:{}失败", key, e);
            return java.util.Set.of();
        }
    }

    public java.util.List<V> multiGet(java.util.List<String> keys) {
        try {
            java.util.List<V> result = redisTemplate.opsForValue().multiGet(keys);
            return result != null ? result : java.util.List.of();
        } catch (Exception e) {
            logger.error("multiGet失败", e);
            return java.util.List.of();
        }
    }
}
