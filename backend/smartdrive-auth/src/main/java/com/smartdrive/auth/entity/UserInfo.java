package com.smartdrive.auth.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartdrive.common.enums.DateTimePatternEnum;
import com.smartdrive.common.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@TableName("user_info")
public class UserInfo implements Serializable {
    @TableId
    private String userId;
    private String nickName;
    private String email;
    private String avatar;
    private String password;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date joinTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastLoginTime;

    private Integer status;
    private Long useSpace;
    private Long totalSpace;
    private String departmentId;
    private Boolean isAdmin;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Date getJoinTime() { return joinTime; }
    public void setJoinTime(Date joinTime) { this.joinTime = joinTime; }
    public Date getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(Date lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getUseSpace() { return useSpace; }
    public void setUseSpace(Long useSpace) { this.useSpace = useSpace; }
    public Long getTotalSpace() { return totalSpace; }
    public void setTotalSpace(Long totalSpace) { this.totalSpace = totalSpace; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public Boolean getIsAdmin() { return isAdmin; }
    public void setIsAdmin(Boolean isAdmin) { this.isAdmin = isAdmin; }

    @Override
    public String toString() {
        return "用户ID:" + (userId == null ? "空" : userId) + "," +
                "昵称:" + (nickName == null ? "空" : nickName) + "," +
                "邮箱:" + (email == null ? "空" : email) + "," +
                "头像:" + (avatar == null ? "空" : avatar) + "," +
                "密码:" + (password == null ? "空" : password) + "," +
                "加入时间:" + (joinTime == null ? "空" : DateUtils.format(joinTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + "," +
                "最后登录时间:" + (lastLoginTime == null ? "空" : DateUtils.format(lastLoginTime, DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern())) + "," +
                "0：禁用   1：启用:" + (status == null ? "空" : status) + "," +
                "使用空间 单位byte:" + (useSpace == null ? "空" : useSpace) + "," +
                "总空间:" + (totalSpace == null ? "空" : totalSpace);
    }
}
