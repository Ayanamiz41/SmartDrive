package com.smartdrive.file.component;

import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.dto.DownloadFileDto;
import com.smartdrive.common.dto.UploadTaskDto;
import com.smartdrive.common.dto.UserSpaceDto;
import com.smartdrive.common.utils.RedisUtils;
import org.springframework.stereotype.Component;

@Component("redisComponent")
public class FileRedisComponent {
    private final RedisUtils<Object> redisUtils;

    public FileRedisComponent(RedisUtils<Object> redisUtils) {
        this.redisUtils = redisUtils;
    }

    public void saveUserSpace(String userId, UserSpaceDto userSpaceDto) {
        redisUtils.setex(Constants.REDIS_KEY_USER_SPACE + userId, userSpaceDto, Constants.REDIS_KEY_EXPIRES_DAY);
    }

    public UserSpaceDto getUserSpace(String userId) {
        return (UserSpaceDto) redisUtils.get(Constants.REDIS_KEY_USER_SPACE + userId);
    }

    public Long getFileTempSize(String userId, String fileId) {
        return getFileSizeFromRedis(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId);
    }

    public void saveFileTempSize(String userId, String fileId, Long fileSize) {
        Long currentSize = getFileTempSize(userId, fileId);
        redisUtils.setex(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId,
                fileSize + currentSize, Constants.REDIS_KEY_EXPIRES_ONE_HOUR);
    }

    private Long getFileSizeFromRedis(String key) {
        Object sizeObj = redisUtils.get(key);
        if (sizeObj == null) return 0L;
        if (sizeObj instanceof Integer) return ((Integer) sizeObj).longValue();
        if (sizeObj instanceof Long) return (Long) sizeObj;
        return 0L;
    }

    public void saveDownloadCode(DownloadFileDto downloadFileDto) {
        redisUtils.setex(Constants.REDIS_KEY_DOWNLOAD + downloadFileDto.getDownloadCode(),
                downloadFileDto, Constants.REDIS_KEY_EXPIRES_FIVE_MIN);
    }

    public DownloadFileDto getDownloadDto(String code) {
        return (DownloadFileDto) redisUtils.get(Constants.REDIS_KEY_DOWNLOAD + code);
    }

    // ========== 上传断点续传 ==========

    public void saveUploadTask(String userId, UploadTaskDto task) {
        String key = Constants.REDIS_KEY_UPLOAD_TASK + userId + ":" + task.getFileMd5();
        redisUtils.setex(key, task, Constants.REDIS_KEY_EXPIRES_DAY);
    }

    public UploadTaskDto getUploadTask(String userId, String fileMd5) {
        return (UploadTaskDto) redisUtils.get(Constants.REDIS_KEY_UPLOAD_TASK + userId + ":" + fileMd5);
    }

    public void deleteUploadTask(String userId, String fileMd5) {
        redisUtils.del(Constants.REDIS_KEY_UPLOAD_TASK + userId + ":" + fileMd5);
    }

}
