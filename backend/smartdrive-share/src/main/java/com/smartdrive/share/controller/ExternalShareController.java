package com.smartdrive.share.controller;

import com.smartdrive.common.annotation.AuditAction;
import com.smartdrive.common.annotation.AuditLog;
import com.smartdrive.common.annotation.TargetType;
import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.dto.SessionShareDto;
import com.smartdrive.common.dto.SessionWebUserDto;
import com.smartdrive.common.enums.FileDelFlagEnum;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.common.vo.FileInfoVO;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.common.vo.ShareInfoVO;
import com.smartdrive.share.feign.FileFeignClient;
import com.smartdrive.share.service.ExternalShareService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Map;

@RestController
@RequestMapping("/showShare")
public class ExternalShareController extends BaseController {

    private final ExternalShareService externalShareService;
    private final com.smartdrive.share.feign.FileFeignClient fileFeignClient;

    public ExternalShareController(ExternalShareService externalShareService,
                                    com.smartdrive.share.feign.FileFeignClient fileFeignClient) {
        this.externalShareService = externalShareService;
        this.fileFeignClient = fileFeignClient;
    }

    // === 核心算法：获取分享信息 — 完整保留 ===
    @RequestMapping("/getShareInfo")
    public ResponseVO getShareInfo(@RequestParam String shareId) {
        return getSuccessResponseVO(externalShareService.getShareInfoCommon(shareId));
    }

    // === 核心算法：获取分享登录态 — 完整保留 ===
    @RequestMapping("/getShareLoginInfo")
    public ResponseVO getShareLoginInfo(HttpSession session, @RequestParam String shareId) {
        SessionShareDto sessionShareDto = getSessionShareFromSession(session, shareId);
        if (sessionShareDto == null) { return getSuccessResponseVO(null); }
        ShareInfoVO shareInfoVO = externalShareService.getShareInfoCommon(shareId);
        String currentUserId = getCurrentUserId();
        shareInfoVO.setCurrentUser(currentUserId != null && currentUserId.equals(sessionShareDto.getShareUserId()));
        // 转存角色信息：管理员不允许转存；部门主管可转存到部门空间
        shareInfoVO.setAdmin(isAdmin());
        boolean canSaveToDept = false;
        String deptId = getCurrentUserDepartmentId();
        if (!isAdmin() && currentUserId != null && deptId != null && !deptId.isEmpty()) {
            try {
                canSaveToDept = fileFeignClient.isDeptHead(deptId, currentUserId);
            } catch (Exception e) {
                // Feign 失败按非主管处理，不阻断分享页
            }
        }
        shareInfoVO.setCanSaveToDept(canSaveToDept);
        shareInfoVO.setSaveDeptId(canSaveToDept ? deptId : null);
        return getSuccessResponseVO(shareInfoVO);
    }

    // === 核心算法：校验提取码 — 完整保留 ===
    @RequestMapping("/checkShareCode")
    public ResponseVO checkShareCode(HttpSession session,
                                      @RequestParam String shareId, @RequestParam String code) {
        SessionShareDto sessionShareDto = externalShareService.checkShareCode(shareId, code);
        session.setAttribute(Constants.SESSION_SHARE_KEY + shareId, sessionShareDto);
        return getSuccessResponseVO(null);
    }

    // === 核心算法：分享文件列表 — 完整保留 ===
    @RequestMapping("/loadFileList")
    public ResponseVO loadFileList(HttpSession session,
                                    @RequestParam String shareId, @RequestParam String filePid,
                                    @RequestParam(defaultValue = "1") Integer pageNo,
                                    @RequestParam(defaultValue = "15") Integer pageSize,
                                    @RequestParam(required = false) String orderBy) {
        SessionShareDto sessionShareDto = checkShare(session, shareId);

        java.util.List<Map<String, Object>> allFiles = new java.util.ArrayList<>();
        try {
            if ("0".equals(filePid)) {
                // 根层级：显示被分享的文件/文件夹本身（与原版一致），文件夹点击后进入
                Map<String, Object> rootInfo = fileFeignClient.getFileInfo(
                        sessionShareDto.getFileId(), sessionShareDto.getShareUserId());
                if (rootInfo != null && !rootInfo.isEmpty()) {
                    allFiles.add(rootInfo);
                }
            } else {
                // 子文件夹：返回其子文件列表
                allFiles = fileFeignClient.listFilesByPid(filePid, null);
            }
        } catch (Exception e) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        // 排序
        if (orderBy != null && !orderBy.isEmpty()) {
            String[] parts = orderBy.split(" ");
            String field = parts[0];
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
            java.util.Comparator<Map<String, Object>> comp = (a, b) -> {
                Object va = a.get(field);
                Object vb = b.get(field);
                if (va == null && vb == null) return 0;
                if (va == null) return 1;
                if (vb == null) return -1;
                int cmp;
                if (va instanceof Comparable && vb instanceof Comparable) {
                    cmp = ((Comparable) va).compareTo(vb);
                } else {
                    cmp = va.toString().compareTo(vb.toString());
                }
                return desc ? -cmp : cmp;
            };
            allFiles.sort(comp);
        }
        // 简易分页
        int total = allFiles.size();
        int start = (pageNo - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        java.util.List<Map<String, Object>> pageList = start < total ? allFiles.subList(start, end) : java.util.List.of();

        Map<String, Object> pageResult = new java.util.HashMap<>();
        pageResult.put("list", pageList);
        pageResult.put("totalCount", total);
        pageResult.put("pageNo", pageNo);
        pageResult.put("pageSize", pageSize);
        return getSuccessResponseVO(pageResult);
    }

    // === 核心算法：校验分享 — 完整保留 ===
    private SessionShareDto checkShare(HttpSession session, String shareId) {
        SessionShareDto sessionShareDto = getSessionShareFromSession(session, shareId);
        if (sessionShareDto == null) { throw new BusinessException(ResponseCodeEnum.CODE_903); }
        if (sessionShareDto.getExpireTime() != null && new Date().after(sessionShareDto.getExpireTime())) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        return sessionShareDto;
    }

    // === 核心算法：分享文件夹路径 — 完整保留 ===
    @RequestMapping("/getFolderInfo")
    public ResponseVO getFolderInfo(HttpSession session,
                                     @RequestParam String shareId, @RequestParam String path) {
        SessionShareDto sessionShareDto = checkShare(session, shareId);
        java.util.List<Map<String, Object>> result = fileFeignClient.getFolderPath(path, sessionShareDto.getShareUserId());
        return getSuccessResponseVO(result);
    }

    // === 分享文件下载等，通过 Feign 委托给 file-service ===
    @RequestMapping("/getFile/{shareId}/{fileId}")
    public void getFile(HttpServletResponse response, HttpSession session,
                        @PathVariable String shareId, @PathVariable String fileId) throws Exception {
        SessionShareDto dto = checkShare(session, shareId);
        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> entity =
                fileFeignClient.getFileContent(fileId);
        if (entity.getBody() != null) {
            response.setContentType("application/octet-stream");
            org.springframework.util.StreamUtils.copy(entity.getBody().getInputStream(), response.getOutputStream());
        }
    }

    @RequestMapping("/ts/getVideoInfo/{shareId}/{fileId}")
    public void getVideoInfo(HttpServletResponse response, HttpSession session,
                             @PathVariable String shareId, @PathVariable String fileId) throws Exception {
        SessionShareDto dto = checkShare(session, shareId);
        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> entity =
                fileFeignClient.getFileContent(fileId);
        if (entity.getBody() != null) {
            String contentType = java.util.Objects.toString(entity.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_TYPE), "video/mp4");
            response.setContentType(contentType);
            org.springframework.util.StreamUtils.copy(entity.getBody().getInputStream(), response.getOutputStream());
        }
    }

    @RequestMapping("/createDownloadUrl/{shareId}/{fileId}")
    public ResponseVO createDownloadUrl(HttpSession session,
                                         @PathVariable String shareId, @PathVariable String fileId) {
        checkShare(session, shareId);
        Map<String, String> result = fileFeignClient.createDownloadCode(fileId);
        return getSuccessResponseVO(result.get("code"));
    }

    @GetMapping("/download/{code}")
    public void download(HttpServletRequest request, HttpServletResponse response, @PathVariable String code) throws Exception {
        org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> entity =
                fileFeignClient.downloadByCode(code);
        if (entity.getBody() != null) {
            response.setContentType("application/x-msdownload;charset=UTF-8");
            String fileName = java.net.URLEncoder.encode("download", "UTF-8");
            String cd = entity.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION);
            if (cd != null) response.setHeader("Content-Disposition", cd);
            org.springframework.util.StreamUtils.copy(entity.getBody().getInputStream(), response.getOutputStream());
        }
    }

    // === 核心算法：转存 — 完整保留 ===
    @PostMapping("/saveShare")
    @AuditLog(value = AuditAction.SAVE_SHARE, targetType = TargetType.FILE,
              targetIdParam = "shareFileIds")
    public ResponseVO saveShare(HttpSession session,
                                 @RequestParam String shareId, @RequestParam String shareFileIds,
                                 @RequestParam String myFolderId,
                                 @RequestParam(defaultValue = "false") Boolean saveToDept) {
        SessionShareDto dto = checkShare(session, shareId);
        String currentUserId = getCurrentUserId();
        if (isAdmin()) {
            throw new BusinessException("管理员不允许转存文件");
        }
        if (dto.getShareUserId().equals(currentUserId)) {
            throw new BusinessException("自己分享的文件无法保存到自己的网盘");
        }
        String targetDeptId = null;
        if (saveToDept) {
            String deptId = getCurrentUserDepartmentId();
            if (deptId == null || deptId.isEmpty() || !fileFeignClient.isDeptHead(deptId, currentUserId)) {
                throw new BusinessException("仅部门主管可转存到部门空间");
            }
            targetDeptId = deptId;
        }
        Map<String, Object> saveResult = fileFeignClient.saveShare(dto.getFileId(), shareFileIds, myFolderId, dto.getShareUserId(), targetDeptId, currentUserId);
        if (saveResult != null && "error".equals(saveResult.get("status"))) {
            throw new BusinessException(String.valueOf(saveResult.getOrDefault("info", "转存失败")));
        }
        return getSuccessResponseVO(null);
    }
}
