package com.smartdrive.common.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.io.Serializable;

/**
 * 上传任务信息 — 前端刷新后通过 /upload/resume 恢复上传进度
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UploadTaskDto implements Serializable {
    private String fileId;
    private String fileName;
    private String fileMd5;
    private int chunks;
    private int completedChunks;
}
