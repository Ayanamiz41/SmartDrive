package com.smartdrive.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartdrive.auth.entity.UserInfo;
import com.smartdrive.auth.entity.query.UserInfoQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserInfoMapper extends BaseMapper<UserInfo> {

    List<UserInfo> selectList(@Param("query") UserInfoQuery query);

    Integer selectCount(@Param("query") UserInfoQuery query);

    UserInfo selectByUserId(@Param("userId") String userId);

    UserInfo selectByEmail(@Param("email") String email);

    UserInfo selectByNickName(@Param("nickName") String nickName);

    Integer updateByUserId(@Param("bean") UserInfo bean, @Param("userId") String userId);

    Integer updateByEmail(@Param("bean") UserInfo bean, @Param("email") String email);

    Integer updateByNickName(@Param("bean") UserInfo bean, @Param("nickName") String nickName);

    Integer deleteByUserId(@Param("userId") String userId);

    Integer deleteByEmail(@Param("email") String email);

    Integer deleteByNickName(@Param("nickName") String nickName);

    Integer updateUserSpace(@Param("userId") String userId,
                            @Param("AddUseSpace") Long AddUseSpace,
                            @Param("totalSpace") Long totalSpace);

    Integer insertBatch(@Param("list") List<UserInfo> list);

    Integer insertOrUpdateBatch(@Param("list") List<UserInfo> list);

    Integer updateUserDepartment(@Param("userId") String userId, @Param("departmentId") String departmentId);

    List<UserInfo> selectByDepartmentId(@Param("departmentId") String departmentId);

    Integer updateUserSpaceAndUsed(@Param("userId") String userId,
                                   @Param("useSpace") Long useSpace,
                                   @Param("totalSpace") Long totalSpace);
}
