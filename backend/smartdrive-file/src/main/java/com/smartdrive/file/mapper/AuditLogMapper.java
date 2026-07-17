package com.smartdrive.file.mapper;

import com.smartdrive.file.entity.AuditLogEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AuditLogMapper {

    void insert(@Param("entry") AuditLogEntry entry);

    List<AuditLogEntry> selectList(@Param("userId") String userId,
                                   @Param("action") String action,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("deptUserIds") List<String> deptUserIds,
                                   @Param("departmentId") String departmentId,
                                   @Param("keyword") String keyword,
                                   @Param("orderBy") String orderBy,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    int selectCount(@Param("userId") String userId,
                    @Param("action") String action,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime,
                    @Param("deptUserIds") List<String> deptUserIds,
                    @Param("departmentId") String departmentId,
                    @Param("keyword") String keyword);
}
