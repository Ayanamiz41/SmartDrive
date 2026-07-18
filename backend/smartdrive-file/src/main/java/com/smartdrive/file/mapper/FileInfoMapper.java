package com.smartdrive.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.entity.query.FileInfoQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface FileInfoMapper extends BaseMapper<FileInfo> {

    List<FileInfo> selectList(@Param("query") FileInfoQuery query);

    Integer selectCount(@Param("query") FileInfoQuery query);

    FileInfo selectByFileIdAndUserId(@Param("fileId") String fileId, @Param("userId") String userId);

    FileInfo selectByFileId(@Param("fileId") String fileId);

    Integer updateByFileIdAndUserId(@Param("bean") FileInfo bean,
                                    @Param("fileId") String fileId,
                                    @Param("userId") String userId);

    Integer updateByFileId(@Param("bean") FileInfo bean,
                           @Param("fileId") String fileId);

    Integer deleteByFileIdAndUserId(@Param("fileId") String fileId, @Param("userId") String userId);

    Integer updateFileDelFlagBatch(@Param("bean") FileInfo bean,
                                   @Param("userId") String userId,
                                   @Param("filePid") String filePid,
                                   @Param("fileIdList") List<String> fileIdList,
                                   @Param("oldDelFlag") Integer oldDelFlag);

    Integer updateFileDelFlagByIds(@Param("bean") FileInfo bean,
                                    @Param("fileIdList") List<String> fileIdList,
                                    @Param("oldDelFlag") Integer oldDelFlag);

    Integer deleteFileBatchWithOldDelFlag(@Param("userId") String userId,
                                          @Param("filePid") String filePid,
                                          @Param("fileIdList") List<String> fileIdList,
                                          @Param("oldDelFlag") Integer oldDelFlag);

    Long selectUseSpaceByUserId(@Param("userId") String userId);

    Integer insertBatch(@Param("list") List<FileInfo> list);

    Integer insertOrUpdateBatch(@Param("list") List<FileInfo> list);

    Integer updateWithOldStatus(@Param("fileId") String fileId,
                                @Param("userId") String userId,
                                @Param("bean") FileInfo bean,
                                @Param("oldStatus") Integer oldStatus);

    Integer deleteFileByUserId(@Param("userId") String userId);

    int countByFilePath(@Param("filePath") String filePath, @Param("delFlag") Integer delFlag);

    int countByFileCover(@Param("fileCover") String fileCover, @Param("delFlag") Integer delFlag);

    List<FileInfo> selectBasicByIds(@Param("fileIds") List<String> fileIds);

    List<String> selectUserIdsByNickName(@Param("nickNameFuzzy") String nickNameFuzzy);

    List<FileInfo> selectListPage(@Param("query") FileInfoQuery query,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);
}
