package com.smartdrive.auth.service;

import com.smartdrive.auth.entity.UserInfo;
import com.smartdrive.auth.entity.query.UserInfoQuery;
import com.smartdrive.common.dto.SessionWebUserDto;
import com.smartdrive.common.vo.PaginationResultVO;

import java.util.List;

public interface UserInfoService {
    List<UserInfo> findListByParam(UserInfoQuery query);
    Integer findCountByParam(UserInfoQuery query);
    PaginationResultVO<UserInfo> findListByPage(UserInfoQuery query);
    Integer add(UserInfo bean);
    Integer addBatch(List<UserInfo> listBean);
    Integer addOrUpdateBatch(List<UserInfo> listBean);

    UserInfo getUserInfoByUserId(String userId);
    Integer updateUserInfoByUserId(UserInfo bean, String userId);
    Integer deleteUserInfoByUserId(String userId);
    UserInfo getUserInfoByEmail(String email);
    Integer updateUserInfoByEmail(UserInfo bean, String email);
    Integer deleteUserInfoByEmail(String email);
    UserInfo getUserInfoByNickName(String nickName);
    Integer updateUserInfoByNickName(UserInfo bean, String nickName);
    Integer deleteUserInfoByNickName(String nickName);

    void register(String email, String nickName, String password, String emailCode);
    SessionWebUserDto login(String email, String password);
    void resetPwd(String email, String password, String emailCode);
    boolean isDeptHead(String departmentId, String userId);
    void updateUserStatus(String userId, Integer status);
    void updateUserSpace(String userId, Long changeSpace);

    void assignDepartment(String userId, String departmentId);
    String getUserDepartmentId(String userId);
    boolean isAdmin(String userId);
    Long getRealUseSpace(String userId);
    void updateUseSpace(String userId, Long useSpace);
    void incrementUseSpace(String userId, Long delta);
    List<String> getDepartmentMemberIds(String departmentId);
    void clearDepartmentMembers(String departmentId);
    List<UserInfo> getDepartmentMembers(String departmentId);
}
