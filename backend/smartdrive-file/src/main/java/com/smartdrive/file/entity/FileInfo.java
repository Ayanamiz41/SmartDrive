package com.smartdrive.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.smartdrive.common.enums.DateTimePatternEnum;
import com.smartdrive.common.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

@TableName("file_info")
public class FileInfo implements Serializable {
    @TableId
    private String fileId;
    private String userId;
    private String fileMd5;
    private String filePid;
    private Long fileSize;
    private String fileName;
    private String fileCover;
    private String filePath;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date lastUpdateTime;

    private Integer folderType;
    private Integer fileCategory;
    private Integer fileType;
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date recycleTime;

    private Integer delFlag;
    /** 归档标记 0=正常 1=已归档 */
    private Integer archived;
    /** 归档时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date archivedTime;
    /** 文件/文件夹摘要（手动填写，后续AI自动生成口子；文件夹语义上为"说明"） */
    private String summary;
    @TableField(exist = false)
    private String nickName;

    // 所属部门
    private String departmentId;

    // 最后编辑者ID（非数据库字段，从 JOIN 获取）
    @TableField(exist = false)
    private String lastUpdateUserId;
    @TableField(exist = false)
    private String lastUpdateUserNickName;
    @TableField(exist = false)
    private String departmentName;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public String getFilePid() { return filePid; }
    public void setFilePid(String filePid) { this.filePid = filePid; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileCover() { return fileCover; }
    public void setFileCover(String fileCover) { this.fileCover = fileCover; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public Date getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(Date lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    public Integer getFolderType() { return folderType; }
    public void setFolderType(Integer folderType) { this.folderType = folderType; }
    public Integer getFileCategory() { return fileCategory; }
    public void setFileCategory(Integer fileCategory) { this.fileCategory = fileCategory; }
    public Integer getFileType() { return fileType; }
    public void setFileType(Integer fileType) { this.fileType = fileType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Date getRecycleTime() { return recycleTime; }
    public void setRecycleTime(Date recycleTime) { this.recycleTime = recycleTime; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public Integer getArchived() { return archived; }
    public void setArchived(Integer archived) { this.archived = archived; }
    public Date getArchivedTime() { return archivedTime; }
    public void setArchivedTime(Date archivedTime) { this.archivedTime = archivedTime; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getLastUpdateUserId() { return lastUpdateUserId; }
    public void setLastUpdateUserId(String lastUpdateUserId) { this.lastUpdateUserId = lastUpdateUserId; }
    public String getLastUpdateUserNickName() { return lastUpdateUserNickName; }
    public void setLastUpdateUserNickName(String lastUpdateUserNickName) { this.lastUpdateUserNickName = lastUpdateUserNickName; }
    public String getDepartmentName() { return departmentName; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
}
