package com.smartdrive.file.controller;

import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.enums.PageSize;
import com.smartdrive.common.query.SimplePage;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.file.entity.AuditLogEntry;
import com.smartdrive.file.service.AuditLogService;
import com.smartdrive.file.service.DepartmentService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 操作审计查询
 */
@RestController
@RequestMapping("/admin/audit")
public class AuditLogController extends BaseController {

    private final AuditLogService auditLogService;
    private final DepartmentService departmentService;

    public AuditLogController(AuditLogService auditLogService, DepartmentService departmentService) {
        this.auditLogService = auditLogService;
        this.departmentService = departmentService;
    }

    @RequestMapping("/list")
    public ResponseVO list(@RequestParam(required = false) String action,
                           @RequestParam(required = false) String startDate,
                           @RequestParam(required = false) String endDate,
                           @RequestParam(required = false) String userId,
                           @RequestParam(required = false) String departmentId,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String orderBy,
                           @RequestParam(defaultValue = "1") Integer pageNo,
                           @RequestParam(defaultValue = "15") Integer pageSize) {

        String currentUserId = getCurrentUserId();
        boolean isAdmin = isAdmin();

        // 权限范围
        String queryUserId = null;
        String queryDeptId = null;

        if (isAdmin) {
            // 管理员：可选筛选部门；不传 departmentId 则看全部
            if (departmentId != null && !departmentId.isEmpty()) {
                queryDeptId = departmentId;
            }
            if (userId != null && !userId.isEmpty()) {
                queryUserId = userId;
            }
        } else {
            // 非管理员：先看是不是部门主管
            String deptId = getCurrentUserDepartmentId();
            if (deptId != null && departmentService.isDeptHead(deptId, currentUserId)) {
                queryDeptId = deptId;
                if (userId != null && !userId.isEmpty()) {
                    queryUserId = userId;
                }
            } else {
                queryUserId = currentUserId; // 普通成员只看自己
            }
        }

        LocalDateTime startTime = null;
        LocalDateTime endTime = null;
        if (startDate != null && !startDate.isEmpty()) {
            startTime = LocalDate.parse(startDate).atStartOfDay();
        }
        if (endDate != null && !endDate.isEmpty()) {
            endTime = LocalDate.parse(endDate).atTime(LocalTime.MAX);
        }

        int total = auditLogService.countAuditLogs(queryUserId, action, startTime, endTime, null, queryDeptId, keyword);
        SimplePage page = new SimplePage(pageNo, total, pageSize);
        List<AuditLogEntry> list = auditLogService.listAuditLogs(
                queryUserId, action, startTime, endTime, null, queryDeptId, keyword,
                orderBy != null ? orderBy : "created_at DESC",
                page.getStart(), page.getEnd());

        PaginationResultVO<AuditLogEntry> result =
                new PaginationResultVO<>(total, pageSize, pageNo, page.getPageTotal(), list);
        return getSuccessResponseVO(result);
    }
}
