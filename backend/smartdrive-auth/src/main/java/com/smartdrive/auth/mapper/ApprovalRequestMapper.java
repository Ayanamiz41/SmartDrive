package com.smartdrive.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartdrive.auth.entity.ApprovalRequest;
import com.smartdrive.auth.entity.query.ApprovalQuery;
import com.smartdrive.auth.entity.vo.ApprovalVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ApprovalRequestMapper extends BaseMapper<ApprovalRequest> {

    List<ApprovalVO> selectList(@Param("query") ApprovalQuery query);

    Integer selectCount(@Param("query") ApprovalQuery query);

    ApprovalRequest selectById(@Param("id") String id);

    Integer insertBean(@Param("bean") ApprovalRequest bean);

    Integer updateStatus(@Param("id") String id,
                          @Param("status") Integer status,
                          @Param("comment") String comment,
                          @Param("handleTime") java.time.LocalDateTime handleTime);

    Integer updateResubmit(@Param("id") String id,
                            @Param("content") String content,
                            @Param("approverId") String approverId,
                            @Param("createTime") java.time.LocalDateTime createTime);
}
