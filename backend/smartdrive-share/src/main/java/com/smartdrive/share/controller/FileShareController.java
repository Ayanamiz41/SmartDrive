package com.smartdrive.share.controller;

import com.smartdrive.common.annotation.AuditAction;
import com.smartdrive.common.annotation.AuditLog;
import com.smartdrive.common.annotation.TargetType;
import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.share.entity.FileShare;
import com.smartdrive.share.entity.query.FileShareQuery;
import com.smartdrive.share.feign.FileFeignClient;
import com.smartdrive.share.service.FileShareService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/share")
public class FileShareController extends BaseController {

    private final FileShareService fileShareService;
    private final FileFeignClient fileFeignClient;

    public FileShareController(FileShareService fileShareService, FileFeignClient fileFeignClient) {
        this.fileShareService = fileShareService;
        this.fileFeignClient = fileFeignClient;
    }

    @RequestMapping("/loadShareList")
    public ResponseVO loadShareList(Integer pageNo, Integer pageSize,
                                     @RequestParam(required = false) String orderBy) {
        FileShareQuery query = new FileShareQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setUserId(getCurrentUserId());
        query.setOrderBy(orderBy);
        var result = fileShareService.findListByPage(query);

        // 批量 Feign 获取文件名
        List<FileShare> shares = result.getList();
        if (shares != null && !shares.isEmpty()) {
            List<String> fileIds = shares.stream().map(FileShare::getFileId).collect(Collectors.toList());
            Map<String, String> nameMap = fileFeignClient.getFileNames(fileIds, getCurrentUserId());
            shares.forEach(s -> s.setFileName(nameMap.getOrDefault(s.getFileId(), null)));
        }

        return getSuccessResponseVO(result);
    }

    @PostMapping("/shareFile")
    @AuditLog(value = AuditAction.SHARE, targetType = TargetType.FILE,
              targetIdParam = "fileId")
    public ResponseVO shareFile(@RequestParam String fileId,
                                 @RequestParam Integer validType, String code) {
        // 权限检查：仅管理员、部门主管可分享；普通员工（含个人空间文件）一律不允许
        String userId = getCurrentUserId();
        if (!isAdmin()) {
            String myDeptId = getCurrentUserDepartmentId();
            if (myDeptId == null || myDeptId.isEmpty() || !fileFeignClient.isDeptHead(myDeptId, userId)) {
                throw new BusinessException("仅部门主管可分享文件");
            }
            // 主管只能分享自己的文件或本部门的文件
            Map<String, Object> fileInfo = fileFeignClient.getFileInfo(fileId, userId);
            String fileDeptId = (String) fileInfo.get("departmentId");
            if (!userId.equals(fileInfo.get("userId")) && !myDeptId.equals(fileDeptId)) {
                throw new BusinessException("无权分享此文件");
            }
        }
        FileShare shareFile = new FileShare();
        shareFile.setFileId(fileId);
        shareFile.setValidType(validType);
        shareFile.setCode(code);
        shareFile.setUserId(getCurrentUserId());
        fileShareService.saveShare(shareFile);
        return getSuccessResponseVO(shareFile);
    }

    @PostMapping("/cancelShare")
    @AuditLog(value = AuditAction.CANCEL_SHARE, targetType = TargetType.SHARE,
              targetIdParam = "shareIds")
    public ResponseVO cancelShare(@RequestParam String shareIds) {
        fileShareService.deleteFileShareBatch(shareIds.split(","), getCurrentUserId());
        return getSuccessResponseVO(null);
    }
}
