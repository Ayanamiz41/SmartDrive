package com.smartdrive.common.dto;

import lombok.Data;

@Data
public class SessionWebUserDto {
    private String nickName;
    private String userId;
    private Boolean admin;
    private String avatar;
    private String departmentId;
    /** 是否部门主管（登录时判定；变更需重新登录生效） */
    private Boolean deptHead;
    /** 上传时填写的文件摘要 */
    private String summary;
}
