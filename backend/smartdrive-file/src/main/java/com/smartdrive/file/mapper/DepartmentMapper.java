package com.smartdrive.file.mapper;

import com.smartdrive.file.entity.Department;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DepartmentMapper {

    List<Department> selectAll();

    Department selectById(@Param("id") String id);

    int insert(@Param("dept") Department dept);

    int update(@Param("dept") Department dept);

    int deleteById(@Param("id") String id);

    int setHeadUser(@Param("id") String id, @Param("headUserId") String headUserId);

    String getHeadUserId(@Param("id") String id);

    Department selectByName(@Param("name") String name, @Param("excludeId") String excludeId);
}
