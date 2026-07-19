package com.smartdrive.share.service.impl;

import com.smartdrive.common.dto.SessionShareDto;
import com.smartdrive.common.enums.FileDelFlagEnum;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.vo.ShareInfoVO;
import com.smartdrive.share.entity.FileShare;
import com.smartdrive.share.entity.query.FileShareQuery;
import com.smartdrive.share.mapper.FileShareMapper;
import com.smartdrive.share.feign.AuthFeignClient;
import com.smartdrive.share.feign.FileFeignClient;
import com.smartdrive.share.service.ExternalShareService;
import com.smartdrive.share.service.FileShareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Service
public class ExternalShareServiceImpl implements ExternalShareService {

    private static final Logger log = LoggerFactory.getLogger(ExternalShareServiceImpl.class);
    private final FileShareService fileShareService;
    private final FileShareMapper fileShareMapper;
    private final AuthFeignClient authFeignClient;
    private final FileFeignClient fileFeignClient;

    public ExternalShareServiceImpl(FileShareService fileShareService, FileShareMapper fileShareMapper,
                                     AuthFeignClient authFeignClient, FileFeignClient fileFeignClient) {
        this.fileShareService = fileShareService;
        this.fileShareMapper = fileShareMapper;
        this.authFeignClient = authFeignClient;
        this.fileFeignClient = fileFeignClient;
    }

    // === 核心算法：获取分享信息 — 完整保留 ===
    @Override
    public ShareInfoVO getShareInfoCommon(String shareId) {
        FileShare fileShare = fileShareService.getFileShareByShareId(shareId);
        if (fileShare == null || (fileShare.getValidType() != 3 && new Date().after(fileShare.getExpireTime()))) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        ShareInfoVO shareInfoVO = new ShareInfoVO();
        shareInfoVO.setShareId(fileShare.getShareId());
        shareInfoVO.setFileId(fileShare.getFileId());
        shareInfoVO.setUserId(fileShare.getUserId());
        shareInfoVO.setValidType(fileShare.getValidType());
        shareInfoVO.setExpireTime(fileShare.getExpireTime());
        shareInfoVO.setShareTime(fileShare.getShareTime());
        shareInfoVO.setCode(fileShare.getCode());
        shareInfoVO.setShowCount(fileShare.getShowCount());
        shareInfoVO.setFileName(fileShare.getFileName());
        shareInfoVO.setFolderType(fileShare.getFolderType());
        shareInfoVO.setFileCategory(fileShare.getFileCategory());
        shareInfoVO.setFileType(fileShare.getFileType());
        shareInfoVO.setFileCover(fileShare.getFileCover());

        // 通过 Feign 调 auth-service 获取分享者昵称
        try {
            Map<String, Object> userInfo = authFeignClient.getUserInfo(fileShare.getUserId());
            if (userInfo != null && userInfo.get("nickName") != null) {
                shareInfoVO.setNickName((String) userInfo.get("nickName"));
            }
        } catch (Exception e) {
            log.warn("获取分享者昵称失败", e);
        }
        if (shareInfoVO.getNickName() == null) {
            shareInfoVO.setNickName(fileShare.getUserId());
        }

        // 校验文件是否存在
        try {
            Map<String, Object> fileInfo = fileFeignClient.getFileInfo(
                    fileShare.getFileId(), fileShare.getUserId());
            shareInfoVO.setFileDeleted(fileInfo == null || fileInfo.isEmpty());
        } catch (Exception e) {
            log.warn("校验分享文件存在性失败", e);
            shareInfoVO.setFileDeleted(true);
        }

        return shareInfoVO;
    }

    // === 核心算法：校验提取码 — 完整保留 ===
    @Override
    public SessionShareDto checkShareCode(String shareId, String code) {
        FileShare fileShare = fileShareService.getFileShareByShareId(shareId);
        if (fileShare == null || (fileShare.getValidType() != 3 && new Date().after(fileShare.getExpireTime()))) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        if (!fileShare.getCode().equals(code)) {
            throw new BusinessException("提取码错误");
        }
        // 检查文件是否已被删除
        try {
            Map<String, Object> fileInfo = fileFeignClient.getFileInfo(
                    fileShare.getFileId(), fileShare.getUserId());
            if (fileInfo == null || fileInfo.isEmpty()) {
                throw new BusinessException("分享的文件已被删除");
            }
        } catch (BusinessException e) { throw e;
        } catch (Exception e) { log.warn("checkShareCode文件校验失败", e); }
        fileShareMapper.updateShareShowCountPlusOne(shareId);
        SessionShareDto sessionShareDto = new SessionShareDto();
        sessionShareDto.setShareId(shareId);
        sessionShareDto.setFileId(fileShare.getFileId());
        sessionShareDto.setShareUserId(fileShare.getUserId());
        sessionShareDto.setExpireTime(fileShare.getExpireTime());
        return sessionShareDto;
    }
}
