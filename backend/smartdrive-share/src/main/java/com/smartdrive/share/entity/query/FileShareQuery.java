package com.smartdrive.share.entity.query;

import com.smartdrive.common.query.BaseQuery;
import java.util.Date;

public class FileShareQuery extends BaseQuery {
    private String shareId;
    private String shareIdFuzzy;
    private String fileId;
    private String fileIdFuzzy;
    private String userId;
    private String userIdFuzzy;
    private Integer validType;
    private Date expireTime;
    private String expireTimeStart;
    private String expireTimeEnd;
    private Date shareTime;
    private String shareTimeStart;
    private String shareTimeEnd;
    private String code;
    private String codeFuzzy;
    private Integer showCount;
    private Boolean queryFileName;

    public String getShareId() { return shareId; }
    public void setShareId(String shareId) { this.shareId = shareId; }
    public String getShareIdFuzzy() { return shareIdFuzzy; }
    public void setShareIdFuzzy(String shareIdFuzzy) { this.shareIdFuzzy = shareIdFuzzy; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileIdFuzzy() { return fileIdFuzzy; }
    public void setFileIdFuzzy(String fileIdFuzzy) { this.fileIdFuzzy = fileIdFuzzy; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserIdFuzzy() { return userIdFuzzy; }
    public void setUserIdFuzzy(String userIdFuzzy) { this.userIdFuzzy = userIdFuzzy; }
    public Integer getValidType() { return validType; }
    public void setValidType(Integer validType) { this.validType = validType; }
    public Date getExpireTime() { return expireTime; }
    public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    public String getExpireTimeStart() { return expireTimeStart; }
    public void setExpireTimeStart(String expireTimeStart) { this.expireTimeStart = expireTimeStart; }
    public String getExpireTimeEnd() { return expireTimeEnd; }
    public void setExpireTimeEnd(String expireTimeEnd) { this.expireTimeEnd = expireTimeEnd; }
    public Date getShareTime() { return shareTime; }
    public void setShareTime(Date shareTime) { this.shareTime = shareTime; }
    public String getShareTimeStart() { return shareTimeStart; }
    public void setShareTimeStart(String shareTimeStart) { this.shareTimeStart = shareTimeStart; }
    public String getShareTimeEnd() { return shareTimeEnd; }
    public void setShareTimeEnd(String shareTimeEnd) { this.shareTimeEnd = shareTimeEnd; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCodeFuzzy() { return codeFuzzy; }
    public void setCodeFuzzy(String codeFuzzy) { this.codeFuzzy = codeFuzzy; }
    public Integer getShowCount() { return showCount; }
    public void setShowCount(Integer showCount) { this.showCount = showCount; }
    public Boolean getQueryFileName() { return queryFileName; }
    public void setQueryFileName(Boolean queryFileName) { this.queryFileName = queryFileName; }
}
