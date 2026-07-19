package com.smartdrive.share.service.impl;

import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.enums.PageSize;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.common.enums.ShareValidTypeEnum;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.query.SimplePage;
import com.smartdrive.common.utils.DateUtils;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.share.entity.FileShare;
import com.smartdrive.share.entity.query.FileShareQuery;
import com.smartdrive.share.mapper.FileShareMapper;
import com.smartdrive.share.service.FileShareService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
public class FileShareServiceImpl implements FileShareService {

    private final FileShareMapper fileShareMapper;

    public FileShareServiceImpl(FileShareMapper fileShareMapper) {
        this.fileShareMapper = fileShareMapper;
    }

    @Override
    public List<FileShare> findListByParam(FileShareQuery query) { return fileShareMapper.selectList(query); }

    @Override
    public Integer findCountByParam(FileShareQuery query) { return fileShareMapper.selectCount(query); }

    @Override
    public PaginationResultVO<FileShare> findListByPage(FileShareQuery query) {
        Integer count = findCountByParam(query);
        Integer pageSize = query.getPageSize() == null ? PageSize.SIZE15.getSize() : query.getPageSize();
        SimplePage page = new SimplePage(query.getPageNo(), count, pageSize);
        query.setSimplePage(page);
        List<FileShare> list = findListByParam(query);
        return new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    @Override
    public Integer add(FileShare bean) { return fileShareMapper.insert(bean); }

    @Override
    public Integer addBatch(List<FileShare> listBean) {
        if (listBean == null || listBean.isEmpty()) return 0;
        return fileShareMapper.insertBatch(listBean);
    }

    @Override
    public Integer addOrUpdateBatch(List<FileShare> listBean) {
        if (listBean == null || listBean.isEmpty()) return 0;
        return fileShareMapper.insertOrUpdateBatch(listBean);
    }

    @Override
    public FileShare getFileShareByShareId(String shareId) { return fileShareMapper.selectByShareId(shareId); }

    @Override
    public Integer updateFileShareByShareId(FileShare bean, String shareId) { return fileShareMapper.updateByShareId(bean, shareId); }

    @Override
    public Integer deleteFileShareByShareId(String shareId) { return fileShareMapper.deleteByShareId(shareId); }

    // === 核心算法：保存分享 — 完整保留 ===
    @Override
    public void saveShare(FileShare fileShare) {
        ShareValidTypeEnum typeEnum = ShareValidTypeEnum.getByType(fileShare.getValidType());
        if (typeEnum == null) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        if (typeEnum != ShareValidTypeEnum.PERMANENT) {
            fileShare.setExpireTime(DateUtils.getDateAfterDays(typeEnum.getDays()));
        }
        fileShare.setShareTime(new Date());
        if (StringTools.isEmpty(fileShare.getCode())) {
            fileShare.setCode(StringTools.getRandomString(Constants.LENGTH_5));
        }
        fileShare.setShowCount(Constants.ZERO);
        fileShare.setShareId(StringTools.getRandomString(Constants.LENGTH_20));
        fileShareMapper.insert(fileShare);
    }

    // === 核心算法：批量取消分享 — 完整保留 ===
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFileShareBatch(String[] shareIdArray, String userId) {
        List<String> shareIdList = Arrays.asList(shareIdArray);
        Integer count = fileShareMapper.deleteFileShareBatch(shareIdList, userId);
        if (count != shareIdArray.length) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
    }
}
