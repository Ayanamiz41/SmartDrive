package com.smartdrive.common.dto;

import lombok.Data;

@Data
public class DownloadFileDto {
    private String fileName;
    private String filePath;
    private String downloadCode;
}
