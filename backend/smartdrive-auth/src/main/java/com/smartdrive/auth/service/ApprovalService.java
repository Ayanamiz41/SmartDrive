package com.smartdrive.auth.service;

import com.smartdrive.auth.entity.vo.ApprovalVO;
import com.smartdrive.common.vo.PaginationResultVO;

public interface ApprovalService {

    /** 分页查询审批列表（根据角色自动切换员工/主管视图） */
    PaginationResultVO<ApprovalVO> listApprovals(Integer status,
                                                  String applicantId,
                                                  String approverId,
                                                  String createTimeStart,
                                                  String createTimeEnd,
                                                  String handleTimeStart,
                                                  String handleTimeEnd,
                                                  Integer pageNo,
                                                  Integer pageSize);

    /** 员工提交审批 */
    void submitApproval(String content, String fileId, String fileName);

    /** 员工撤回审批 */
    void withdrawApproval(String id);

    /** 主管通过审批 */
    void approve(String id, String comment);

    /** 主管驳回审批 */
    void reject(String id, String comment);

    /** 员工重新提交已驳回的审批 */
    void resubmitApproval(String id, String content);

    /** 获取当前主管的待审批数量 */
    int getPendingCount();
}
