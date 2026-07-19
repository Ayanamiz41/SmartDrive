package com.smartdrive.common.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class ShareInfoVO {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date shareTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date expireTime;
    private String nickName;
    private String fileName;
    private Boolean currentUser;
    private String fileId;
    private String avatar;
    private String userId;
    private String shareId;
    private Integer validType;
    private String code;
    private Integer showCount;
    private Integer folderType;
    private Integer fileCategory;
    private Integer fileType;
    private String fileCover;
    private Boolean fileDeleted;
    /** 当前登录用户是否管理员（管理员不允许转存） */
    private Boolean admin;
    /** 当前登录用户是否可转存到部门空间（部门主管） */
    private Boolean canSaveToDept;
    /** 可转存的目标部门ID（主管所在部门） */
    private String saveDeptId;
}
