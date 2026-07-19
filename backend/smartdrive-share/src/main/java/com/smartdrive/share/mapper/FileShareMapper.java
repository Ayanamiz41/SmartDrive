package com.smartdrive.share.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartdrive.share.entity.FileShare;
import com.smartdrive.share.entity.query.FileShareQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FileShareMapper extends BaseMapper<FileShare> {

    List<FileShare> selectList(@Param("query") FileShareQuery query);

    Integer selectCount(@Param("query") FileShareQuery query);

    FileShare selectByShareId(@Param("shareId") String shareId);

    Integer updateByShareId(@Param("bean") FileShare bean, @Param("shareId") String shareId);

    Integer deleteByShareId(@Param("shareId") String shareId);

    Integer deleteFileShareBatch(@Param("shareIdList") List<String> shareIdList, @Param("userId") String userId);

    Integer updateShareShowCountPlusOne(@Param("shareId") String shareId);

    Integer insertBatch(@Param("list") List<FileShare> list);

    Integer insertOrUpdateBatch(@Param("list") List<FileShare> list);
}
