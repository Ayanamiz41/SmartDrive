package com.smartdrive.file.entity.query;

import com.smartdrive.common.query.BaseQuery;

public class FileInfoQuery extends BaseQuery {
    private String fileId;
    private String fileIdFuzzy;
    private String userId;
    private String userIdFuzzy;
    private Boolean queryUserNickName;
    private Boolean queryFileName;
    private String fileMd5;
    private String fileMd5Fuzzy;
    private String filePid;
    private String filePidFuzzy;
    private Long fileSize;
    private String fileName;
    private String fileNameFuzzy;
    private String fileCover;
    private String fileCoverFuzzy;
    private String filePath;
    private String filePathFuzzy;
    private java.util.Date createTime;
    private String createTimeStart;
    private String createTimeEnd;
    private java.util.Date lastUpdateTime;
    private String lastUpdateTimeStart;
    private String lastUpdateTimeEnd;
    private Integer folderType;
    private Integer fileCategory;
    private Integer fileType;
    private Integer status;
    private java.util.Date recycleTime;
    private String recycleTimeStart;
    private String recycleTimeEnd;
    private Integer delFlag;
    /** 归档标记：0=正常 1=已归档 */
    private Integer archived;
    private String[] fileIdArray;
    private String[] excludeFileIdArray;
    private Boolean queryExpire;
    private String departmentId;
    private java.util.List<String> departmentIds;
    private Boolean deptMode;
    private Boolean departmentIdIsNull;
    /** 回收站列表专用：排除父文件夹也在回收站的子文件（不能全局按 delFlag 生效，否则级联收集查询失效） */
    private Boolean excludeNestedRecycle;

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileIdFuzzy() { return fileIdFuzzy; }
    public void setFileIdFuzzy(String fileIdFuzzy) { this.fileIdFuzzy = fileIdFuzzy; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserIdFuzzy() { return userIdFuzzy; }
    public void setUserIdFuzzy(String userIdFuzzy) { this.userIdFuzzy = userIdFuzzy; }
    public Boolean getQueryUserNickName() { return queryUserNickName; }
    public void setQueryUserNickName(Boolean queryUserNickName) { this.queryUserNickName = queryUserNickName; }
    public Boolean getQueryFileName() { return queryFileName; }
    public void setQueryFileName(Boolean queryFileName) { this.queryFileName = queryFileName; }
    public String getFileMd5() { return fileMd5; }
    public void setFileMd5(String fileMd5) { this.fileMd5 = fileMd5; }
    public String getFileMd5Fuzzy() { return fileMd5Fuzzy; }
    public void setFileMd5Fuzzy(String fileMd5Fuzzy) { this.fileMd5Fuzzy = fileMd5Fuzzy; }
    public String getFilePid() { return filePid; }
    public void setFilePid(String filePid) { this.filePid = filePid; }
    public String getFilePidFuzzy() { return filePidFuzzy; }
    public void setFilePidFuzzy(String filePidFuzzy) { this.filePidFuzzy = filePidFuzzy; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileNameFuzzy() { return fileNameFuzzy; }
    public void setFileNameFuzzy(String fileNameFuzzy) { this.fileNameFuzzy = fileNameFuzzy; }
    public String getFileCover() { return fileCover; }
    public void setFileCover(String fileCover) { this.fileCover = fileCover; }
    public String getFileCoverFuzzy() { return fileCoverFuzzy; }
    public void setFileCoverFuzzy(String fileCoverFuzzy) { this.fileCoverFuzzy = fileCoverFuzzy; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFilePathFuzzy() { return filePathFuzzy; }
    public void setFilePathFuzzy(String filePathFuzzy) { this.filePathFuzzy = filePathFuzzy; }
    public java.util.Date getCreateTime() { return createTime; }
    public void setCreateTime(java.util.Date createTime) { this.createTime = createTime; }
    public String getCreateTimeStart() { return createTimeStart; }
    public void setCreateTimeStart(String createTimeStart) { this.createTimeStart = createTimeStart; }
    public String getCreateTimeEnd() { return createTimeEnd; }
    public void setCreateTimeEnd(String createTimeEnd) { this.createTimeEnd = createTimeEnd; }
    public java.util.Date getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(java.util.Date lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    public String getLastUpdateTimeStart() { return lastUpdateTimeStart; }
    public void setLastUpdateTimeStart(String lastUpdateTimeStart) { this.lastUpdateTimeStart = lastUpdateTimeStart; }
    public String getLastUpdateTimeEnd() { return lastUpdateTimeEnd; }
    public void setLastUpdateTimeEnd(String lastUpdateTimeEnd) { this.lastUpdateTimeEnd = lastUpdateTimeEnd; }
    public Integer getFolderType() { return folderType; }
    public void setFolderType(Integer folderType) { this.folderType = folderType; }
    public Integer getFileCategory() { return fileCategory; }
    public void setFileCategory(Integer fileCategory) { this.fileCategory = fileCategory; }
    public Integer getFileType() { return fileType; }
    public void setFileType(Integer fileType) { this.fileType = fileType; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public java.util.Date getRecycleTime() { return recycleTime; }
    public void setRecycleTime(java.util.Date recycleTime) { this.recycleTime = recycleTime; }
    public String getRecycleTimeStart() { return recycleTimeStart; }
    public void setRecycleTimeStart(String recycleTimeStart) { this.recycleTimeStart = recycleTimeStart; }
    public String getRecycleTimeEnd() { return recycleTimeEnd; }
    public void setRecycleTimeEnd(String recycleTimeEnd) { this.recycleTimeEnd = recycleTimeEnd; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public Integer getArchived() { return archived; }
    public void setArchived(Integer archived) { this.archived = archived; }
    public String[] getFileIdArray() { return fileIdArray; }
    public void setFileIdArray(String[] fileIdArray) { this.fileIdArray = fileIdArray; }
    public String[] getExcludeFileIdArray() { return excludeFileIdArray; }
    public void setExcludeFileIdArray(String[] excludeFileIdArray) { this.excludeFileIdArray = excludeFileIdArray; }
    public Boolean getQueryExpire() { return queryExpire; }
    public void setQueryExpire(Boolean queryExpire) { this.queryExpire = queryExpire; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public java.util.List<String> getDepartmentIds() { return departmentIds; }
    public void setDepartmentIds(java.util.List<String> departmentIds) { this.departmentIds = departmentIds; }
    public Boolean getDeptMode() { return deptMode; }
    public void setDeptMode(Boolean deptMode) { this.deptMode = deptMode; }
    public Boolean getDepartmentIdIsNull() { return departmentIdIsNull; }
    public void setDepartmentIdIsNull(Boolean departmentIdIsNull) { this.departmentIdIsNull = departmentIdIsNull; }
    public Boolean getExcludeNestedRecycle() { return excludeNestedRecycle; }
    public void setExcludeNestedRecycle(Boolean excludeNestedRecycle) { this.excludeNestedRecycle = excludeNestedRecycle; }
}
