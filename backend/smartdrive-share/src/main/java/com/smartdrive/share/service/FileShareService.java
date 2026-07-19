package com.smartdrive.share.service;

import com.smartdrive.share.entity.FileShare;
import com.smartdrive.share.entity.query.FileShareQuery;
import com.smartdrive.common.vo.PaginationResultVO;

import java.util.List;

public interface FileShareService {
    List<FileShare> findListByParam(FileShareQuery query);
    Integer findCountByParam(FileShareQuery query);
    PaginationResultVO<FileShare> findListByPage(FileShareQuery query);
    Integer add(FileShare bean);
    Integer addBatch(List<FileShare> listBean);
    Integer addOrUpdateBatch(List<FileShare> listBean);
    FileShare getFileShareByShareId(String shareId);
    Integer updateFileShareByShareId(FileShare bean, String shareId);
    Integer deleteFileShareByShareId(String shareId);
    void saveShare(FileShare fileShare);
    void deleteFileShareBatch(String[] shareIdArray, String userId);
}
