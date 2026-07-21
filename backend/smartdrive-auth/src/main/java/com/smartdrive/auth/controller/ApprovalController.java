package com.smartdrive.auth.controller;

import com.smartdrive.auth.service.ApprovalService;
import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/approval")
public class ApprovalController extends BaseController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** 审批列表（员工/主管自动区分） */
    @RequestMapping("/list")
    public ResponseVO list(@RequestParam(required = false) Integer status,
                           @RequestParam(required = false) String applicantId,
                           @RequestParam(required = false) String approverId,
                           @RequestParam(required = false) String createTimeStart,
                           @RequestParam(required = false) String createTimeEnd,
                           @RequestParam(required = false) String handleTimeStart,
                           @RequestParam(required = false) String handleTimeEnd,
                           @RequestParam(defaultValue = "1") Integer pageNo,
                           @RequestParam(defaultValue = "15") Integer pageSize) {
        PaginationResultVO<?> result = approvalService.listApprovals(
                status, applicantId, approverId,
                createTimeStart, createTimeEnd,
                handleTimeStart, handleTimeEnd,
                pageNo, pageSize);
        return getSuccessResponseVO(result);
    }

    /** 员工提交审批 */
    @PostMapping("/submit")
    public ResponseVO submit(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("申请内容不能为空");
        }
        String fileId = body.get("fileId");
        String fileName = body.get("fileName");
        approvalService.submitApproval(content.trim(), fileId, fileName);
        return getSuccessResponseVO(null);
    }

    /** 员工撤回审批 */
    @PostMapping("/withdraw/{id}")
    public ResponseVO withdraw(@PathVariable String id) {
        approvalService.withdrawApproval(id);
        return getSuccessResponseVO(null);
    }

    /** 主管通过审批 */
    @PostMapping("/approve/{id}")
    public ResponseVO approve(@PathVariable String id, @RequestBody Map<String, String> body) {
        String comment = body.get("comment");
        if (comment == null || comment.trim().isEmpty()) {
            throw new BusinessException("审批意见不能为空");
        }
        approvalService.approve(id, comment.trim());
        return getSuccessResponseVO(null);
    }

    /** 主管驳回审批 */
    @PostMapping("/reject/{id}")
    public ResponseVO reject(@PathVariable String id, @RequestBody Map<String, String> body) {
        String comment = body.get("comment");
        if (comment == null || comment.trim().isEmpty()) {
            throw new BusinessException("审批意见不能为空");
        }
        approvalService.reject(id, comment.trim());
        return getSuccessResponseVO(null);
    }

    /** 员工重新提交已驳回的审批 */
    @PostMapping("/resubmit/{id}")
    public ResponseVO resubmit(@PathVariable String id, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            throw new BusinessException("申请内容不能为空");
        }
        approvalService.resubmitApproval(id, content.trim());
        return getSuccessResponseVO(null);
    }

    /** 获取当前主管待审批数量 */
    @RequestMapping("/pending-count")
    public ResponseVO pendingCount() {
        int count = approvalService.getPendingCount();
        return getSuccessResponseVO(Map.of("count", count));
    }
}
