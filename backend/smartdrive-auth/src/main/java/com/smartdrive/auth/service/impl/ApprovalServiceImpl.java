package com.smartdrive.auth.service.impl;

import com.smartdrive.auth.entity.ApprovalRequest;
import com.smartdrive.auth.entity.query.ApprovalQuery;
import com.smartdrive.auth.entity.vo.ApprovalVO;
import com.smartdrive.auth.feign.FileFeignClient;
import com.smartdrive.auth.mapper.ApprovalRequestMapper;
import com.smartdrive.auth.service.ApprovalService;
import com.smartdrive.auth.service.UserInfoService;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.vo.PaginationResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApprovalServiceImpl implements ApprovalService {

    private static final Logger logger = LoggerFactory.getLogger(ApprovalServiceImpl.class);

    // 状态常量
    private static final int STATUS_PENDING  = 0; // 待审批
    private static final int STATUS_APPROVED = 1; // 已通过
    private static final int STATUS_REJECTED = 2; // 已驳回
    private static final int STATUS_WITHDRAWN = 3; // 已撤回

    private final ApprovalRequestMapper mapper;
    private final UserInfoService userInfoService;
    private final DataSource dataSource;
    private final FileFeignClient fileFeignClient;

    public ApprovalServiceImpl(ApprovalRequestMapper mapper,
                                UserInfoService userInfoService,
                                DataSource dataSource,
                                FileFeignClient fileFeignClient) {
        this.mapper = mapper;
        this.userInfoService = userInfoService;
        this.dataSource = dataSource;
        this.fileFeignClient = fileFeignClient;
    }

    @Override
    public PaginationResultVO<ApprovalVO> listApprovals(Integer status,
                                                         String applicantId,
                                                         String approverId,
                                                         String createTimeStart,
                                                         String createTimeEnd,
                                                         String handleTimeStart,
                                                         String handleTimeEnd,
                                                         Integer pageNo,
                                                         Integer pageSize) {
        String userId = getCurrentUserId();
        boolean isAdmin = isCurrentAdmin();
        boolean isHead = isCurrentDeptHead();

        if (isAdmin) {
            throw new BusinessException("管理员无权访问审批管理");
        }

        ApprovalQuery query = new ApprovalQuery();
        query.setStatus(status);
        query.setApplicantId(applicantId);
        query.setApproverId(approverId);
        query.setPageNo(pageNo != null ? pageNo : 1);
        query.setPageSize(pageSize != null ? pageSize : 15);
        query.setOrderBy("a.create_time DESC");

        // 时间范围
        if (createTimeStart != null && !createTimeStart.isEmpty()) {
            query.setCreateTimeStart(LocalDateTime.parse(createTimeStart));
        }
        if (createTimeEnd != null && !createTimeEnd.isEmpty()) {
            query.setCreateTimeEnd(LocalDateTime.parse(createTimeEnd));
        }
        if (handleTimeStart != null && !handleTimeStart.isEmpty()) {
            query.setHandleTimeStart(LocalDateTime.parse(handleTimeStart));
        }
        if (handleTimeEnd != null && !handleTimeEnd.isEmpty()) {
            query.setHandleTimeEnd(LocalDateTime.parse(handleTimeEnd));
        }

        if (isHead) {
            // 主管：只看自己管辖部门的申请，排除自己
            String deptId = getCurrentUserDepartmentId();
            query.setDepartmentId(deptId);
            query.setExcludeApplicantId(userId);
        } else {
            // 员工：只看自己提交的
            query.setApplicantId(userId);
        }

        List<ApprovalVO> list = mapper.selectList(query);
        int total = mapper.selectCount(query);

        // 批量获取文件信息（跨服务通过 Feign）
        List<String> fileIds = list.stream()
                .map(ApprovalVO::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!fileIds.isEmpty()) {
            try {
                Map<String, Map<String, Object>> fileInfoMap = fileFeignClient.getFileBatchInfo(fileIds);
                for (ApprovalVO vo : list) {
                    if (vo.getFileId() == null) continue;
                    if (fileInfoMap.containsKey(vo.getFileId())) {
                        Map<String, Object> info = fileInfoMap.get(vo.getFileId());
                        vo.setFileDeptId((String) info.get("departmentId"));
                        vo.setFilePid((String) info.get("filePid"));
                        vo.setFileDeleted((Boolean) info.get("deleted"));
                        vo.setFileArchived((Boolean) info.get("archived"));
                    } else {
                        // 文件已被物理删除
                        vo.setFileDeleted(true);
                    }
                }
            } catch (Exception e) {
                logger.warn("批量获取文件信息失败: fileIds={}", fileIds, e);
            }
        }

        PaginationResultVO<ApprovalVO> result = new PaginationResultVO<>();
        result.setList(list);
        result.setPageNo(query.getPageNo());
        result.setPageSize(query.getPageSize());
        result.setTotalCount(total);
        result.setPageTotal((total + query.getPageSize() - 1) / query.getPageSize());
        return result;
    }

    @Override
    @Transactional
    public void submitApproval(String content, String fileId, String fileName) {
        String userId = getCurrentUserId();
        if (isCurrentAdmin()) {
            throw new BusinessException("管理员不能提交审批");
        }
        if (isCurrentDeptHead()) {
            throw new BusinessException("部门主管不能提交审批");
        }

        String deptId = getCurrentUserDepartmentId();
        if (deptId == null || deptId.isEmpty()) {
            throw new BusinessException("您不属于任何部门，无法提交审批");
        }

        // 查部门主管
        String approverId = getDeptHeadId(deptId);
        if (approverId == null) {
            throw new BusinessException("部门未设置主管，无法提交审批");
        }

        ApprovalRequest req = new ApprovalRequest();
        req.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        req.setApplicantId(userId);
        req.setDepartmentId(deptId);
        req.setApproverId(approverId);
        req.setFileId(fileId);
        req.setFileName(fileName);
        req.setContent(content);
        req.setStatus(STATUS_PENDING);
        req.setCreateTime(LocalDateTime.now());

        mapper.insertBean(req);
    }

    @Override
    @Transactional
    public void withdrawApproval(String id) {
        String userId = getCurrentUserId();
        ApprovalRequest req = mapper.selectById(id);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        if (!req.getApplicantId().equals(userId)) {
            throw new BusinessException("只能撤回自己的申请");
        }
        if (req.getStatus() != STATUS_PENDING) {
            throw new BusinessException("只能撤回待审批的申请");
        }
        mapper.updateStatus(id, STATUS_WITHDRAWN, null, null);
    }

    @Override
    @Transactional
    public void approve(String id, String comment) {
        if (!isCurrentDeptHead()) {
            throw new BusinessException("只有部门主管才能审批");
        }
        ApprovalRequest req = mapper.selectById(id);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        if (req.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该申请已处理");
        }
        String userId = getCurrentUserId();
        if (!req.getApproverId().equals(userId)) {
            throw new BusinessException("您不是该申请的审批人");
        }
        mapper.updateStatus(id, STATUS_APPROVED, comment, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void reject(String id, String comment) {
        if (!isCurrentDeptHead()) {
            throw new BusinessException("只有部门主管才能审批");
        }
        ApprovalRequest req = mapper.selectById(id);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        if (req.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该申请已处理");
        }
        String userId = getCurrentUserId();
        if (!req.getApproverId().equals(userId)) {
            throw new BusinessException("您不是该申请的审批人");
        }
        mapper.updateStatus(id, STATUS_REJECTED, comment, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void resubmitApproval(String id, String content) {
        String userId = getCurrentUserId();
        if (isCurrentAdmin()) {
            throw new BusinessException("管理员不能提交审批");
        }
        if (isCurrentDeptHead()) {
            throw new BusinessException("部门主管不能提交审批");
        }
        ApprovalRequest req = mapper.selectById(id);
        if (req == null) {
            throw new BusinessException("申请不存在");
        }
        if (!req.getApplicantId().equals(userId)) {
            throw new BusinessException("只能操作自己的申请");
        }
        if (req.getStatus() != STATUS_REJECTED) {
            throw new BusinessException("只能重新提交已驳回的申请");
        }
        // 重新查部门主管（避免主管已变更仍用旧值）
        String deptId = getCurrentUserDepartmentId();
        String approverId = getDeptHeadId(deptId);
        if (approverId == null) {
            throw new BusinessException("部门未设置主管，无法提交审批");
        }
        mapper.updateResubmit(id, content, approverId, LocalDateTime.now());
    }

    @Override
    public int getPendingCount() {
        if (!isCurrentDeptHead()) return 0;
        String userId = getCurrentUserId();
        ApprovalQuery query = new ApprovalQuery();
        query.setStatus(STATUS_PENDING);
        query.setApproverId(userId);
        return mapper.selectCount(query);
    }

    // ============ 辅助方法 ============

    private String getCurrentUserId() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return ((org.springframework.web.context.request.ServletRequestAttributes) attrs)
                .getRequest().getHeader("X-User-Id");
    }

    private String getCurrentUserDepartmentId() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        String deptId = ((org.springframework.web.context.request.ServletRequestAttributes) attrs)
                .getRequest().getHeader("X-User-DepartmentId");
        return (deptId == null || deptId.isEmpty()) ? null : deptId;
    }

    private boolean isCurrentAdmin() {
        var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attrs == null) return false;
        return "admin".equals(((org.springframework.web.context.request.ServletRequestAttributes) attrs)
                .getRequest().getHeader("X-User-Role"));
    }

    private boolean isCurrentDeptHead() {
        String deptId = getCurrentUserDepartmentId();
        String userId = getCurrentUserId();
        if (deptId == null || userId == null) return false;
        return userInfoService.isDeptHead(deptId, userId);
    }

    private String getDeptHeadId(String deptId) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT head_user_id FROM department WHERE id = ?")) {
            ps.setString(1, deptId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            logger.error("查询部门主管失败: deptId={}", deptId, e);
            return null;
        }
    }
}
