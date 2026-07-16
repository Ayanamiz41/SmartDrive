package com.smartdrive.auth.entity.query;

import com.smartdrive.common.query.BaseQuery;

public class UserInfoQuery extends BaseQuery {
    private String userId;
    private String userIdFuzzy;
    private String nickName;
    private String nickNameFuzzy;
    private String email;
    private String emailFuzzy;
    private String keyword;
    private Integer status;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getUserIdFuzzy() { return userIdFuzzy; }
    public void setUserIdFuzzy(String userIdFuzzy) { this.userIdFuzzy = userIdFuzzy; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getNickNameFuzzy() { return nickNameFuzzy; }
    public void setNickNameFuzzy(String nickNameFuzzy) { this.nickNameFuzzy = nickNameFuzzy; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEmailFuzzy() { return emailFuzzy; }
    public void setEmailFuzzy(String emailFuzzy) { this.emailFuzzy = emailFuzzy; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
