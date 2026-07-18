package com.smartdrive.file.controller;

import com.smartdrive.common.annotation.AuditAction;
import com.smartdrive.common.annotation.AuditLog;
import com.smartdrive.common.annotation.TargetType;
import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.enums.FileDelFlagEnum;
import com.smartdrive.common.vo.FileInfoVO;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.file.entity.query.FileInfoQuery;
import com.smartdrive.file.service.FileInfoService;
import com.smartdrive.file.service.DepartmentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/recycle")
public class RecycleController extends BaseController {

    private final FileInfoService fileInfoService;
    private final DepartmentService departmentService;

    public RecycleController(FileInfoService fileInfoService, DepartmentService departmentService) {
        this.fileInfoService = fileInfoService;
        this.departmentService = departmentService;
    }

    @RequestMapping("/loadRecycleList")
    public ResponseVO loadRecycleList(Integer pageNo, Integer pageSize,
                                       @RequestParam(required = false) String orderBy) {
        FileInfoQuery query = new FileInfoQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setOrderBy(orderBy != null ? orderBy : "recycle_time desc");
        query.setDelFlag(FileDelFlagEnum.RECYCLE.getFlag());
        query.setExcludeNestedRecycle(true);
        String deptId = getCurrentUserDepartmentId();
        if (deptId != null && !deptId.isEmpty() && departmentService.isDeptHead(deptId, getCurrentUserId())) {
            // 部门主管：看部门下所有被删文件（不限上传者）
            query.setDepartmentId(deptId);
        } else {
            // 普通成员：只看自己的
            query.setUserId(getCurrentUserId());
            if (deptId != null && !deptId.isEmpty()) {
                query.setDepartmentId(deptId);
            }
        }
        PaginationResultVO result = fileInfoService.findListByPage(query);
        return getSuccessResponseVO(convert2PaginationVO(result, FileInfoVO.class));
    }

    @PostMapping("/recoverFile")
    @AuditLog(value = AuditAction.RECOVER, targetType = TargetType.FILE,
              targetIdParam = "fileIds")
    public ResponseVO recoverFile(@RequestParam String fileIds) {
        String userId = getCurrentUserId();
        String deptId = getCurrentUserDepartmentId();
        if (deptId != null && !deptId.isEmpty() && departmentService.isDeptHead(deptId, userId)) {
            // 部门主管：按fileId恢复（不限制userId）
            for (String id : fileIds.split(",")) {
                fileInfoService.recoverFileById(userId, id.trim());
            }
        } else {
            fileInfoService.recoverFileBatch(userId, fileIds);
        }
        return getSuccessResponseVO(null);
    }

    @PostMapping("/delFile")
    public ResponseVO delFile(@RequestParam String fileIds) {
        String userId = getCurrentUserId();
        String deptId = getCurrentUserDepartmentId();
        if (deptId != null && !deptId.isEmpty() && departmentService.isDeptHead(deptId, userId)) {
            // 部门主管：按fileId彻底删除（不限制userId），自动更新原上传者空间
            for (String id : fileIds.split(",")) {
                fileInfoService.delFileById(userId, id.trim());
            }
        } else {
            fileInfoService.delFileBatch(userId, fileIds, false);
        }
        return getSuccessResponseVO(null);
    }
}
