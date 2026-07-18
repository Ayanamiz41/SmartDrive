package com.smartdrive.common.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
public class FileInfoVO {
    private String fileId;
    private String filePid;
    private Long fileSize;
    private String fileName;
    private String fileCover;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUpdateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date recycleTime;

    private Integer folderType;
    private Integer fileCategory;
    private Integer fileType;
    private Integer status;
    /** 归档标记 0=正常 1=已归档 */
    private Integer archived;
    /** 归档时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date archivedTime;

    // 上传者
    private String userId;
    private String nickName;

    // 最后编辑者
    private String lastUpdateUserId;
    private String lastUpdateUserNickName;

    // 所属部门
    private String departmentId;
    private String departmentName;

    // 文件/文件夹摘要
    private String summary;

    // 当前用户对此文件的权限
    private String permission;
}
