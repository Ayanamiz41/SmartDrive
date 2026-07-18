package com.smartdrive.file.controller;

import com.smartdrive.common.annotation.AuditAction;
import com.smartdrive.common.annotation.AuditLog;
import com.smartdrive.common.annotation.TargetType;
import com.smartdrive.common.dto.SessionWebUserDto;
import com.smartdrive.common.dto.UploadResultDto;
import com.smartdrive.common.dto.UploadTaskDto;
import com.smartdrive.common.enums.FileCatogoryEnum;
import com.smartdrive.common.enums.FileDelFlagEnum;
import com.smartdrive.common.enums.FileFolderTypeEnum;
import com.smartdrive.common.utils.CopyTools;
import com.smartdrive.common.vo.FileInfoVO;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.common.enums.FileStatusEnum;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.entity.query.FileInfoQuery;
import com.smartdrive.file.feign.AuthFeignClient;
import com.smartdrive.file.mapper.FileInfoMapper;
import com.smartdrive.file.service.FileInfoService;
import com.smartdrive.file.service.DepartmentService;
import com.smartdrive.file.service.AuditLogService;
import com.smartdrive.file.service.AiSummaryService;
import com.smartdrive.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/file")
public class FileInfoController extends CommonFileController {

    private final FileInfoService fileInfoService;
    private final FileInfoMapper fileInfoMapper;
    private final DepartmentService departmentService;
    private final AiSummaryService aiSummaryService;
    private final AuthFeignClient authFeignClient;
    private final AuditLogService auditLogService;

    public FileInfoController(FileInfoService fileInfoService, FileInfoMapper fileInfoMapper,
                              DepartmentService departmentService, AiSummaryService aiSummaryService,
                              AuthFeignClient authFeignClient, AuditLogService auditLogService) {
        this.fileInfoService = fileInfoService;
        this.fileInfoMapper = fileInfoMapper;
        this.departmentService = departmentService;
        this.aiSummaryService = aiSummaryService;
        this.authFeignClient = authFeignClient;
        this.auditLogService = auditLogService;
    }

    @RequestMapping("/loadDataList")
    public ResponseVO loadDataList(FileInfoQuery query, String category,
                                    @RequestParam(defaultValue = "false") Boolean deptMode,
                                    @RequestParam(required = false) String departmentId) {
        FileCatogoryEnum categoryEnum = FileCatogoryEnum.getByCode(category);
        if (categoryEnum != null) { query.setFileCategory(categoryEnum.getCategory()); }
        query.setDeptMode(deptMode);
        if (isAdmin() && departmentId != null && !departmentId.isEmpty()) {
            // 管理员查看指定部门空间
            query.setDepartmentIds(java.util.List.of(departmentId));
            query.setDeptMode(true);
        } else if (deptMode) {
            String deptId = getCurrentUserDepartmentId();
            if (deptId != null && !deptId.isEmpty()) {
                query.setDepartmentIds(java.util.List.of(deptId));
            }
        } else {
            query.setUserId(getCurrentUserId());
        }
        query.setOrderBy(query.getOrderBy() != null ? query.getOrderBy() : "last_update_time desc");
        query.setDelFlag(FileDelFlagEnum.USING.getFlag());
        query.setArchived(0);  // 排除已归档文件
        PaginationResultVO result = fileInfoService.findListByPage(query);
        return getSuccessResponseVO(convert2PaginationVO(result, FileInfoVO.class));
    }

    @PostMapping("/uploadFile")
    public ResponseVO uploadFile(String fileId, MultipartFile file,
                                  @RequestParam String fileName, @RequestParam String filePid,
                                  @RequestParam String fileMd5, @RequestParam Integer chunkIndex,
                                  @RequestParam Integer chunks,
                                  @RequestParam(defaultValue = "false") Boolean deptMode,
                                  @RequestParam(required = false) String departmentId,
                                  @RequestParam(required = false) String summary) {
        SessionWebUserDto dto = new SessionWebUserDto();
        dto.setUserId(getCurrentUserId());
        dto.setAdmin(isAdmin());
        if (departmentId != null && !departmentId.isEmpty()) {
            dto.setDepartmentId(departmentId);
        } else if (deptMode) {
            dto.setDepartmentId(getCurrentUserDepartmentId());
        }
        dto.setSummary(summary);
        UploadResultDto result = fileInfoService.uploadFile(dto, fileId, file, fileName, filePid, fileMd5, chunkIndex, chunks);
        return getSuccessResponseVO(result);
    }

    /** 断点续传：根据 MD5 查找未完成的上传任务，返回 fileId 和已完成分片数 */
    @PostMapping("/upload/resume")
    public ResponseVO resumeUpload(@RequestParam String fileMd5,
                                    @RequestParam String fileName,
                                    @RequestParam Integer chunks) {
        UploadTaskDto task = redisComponent.getUploadTask(getCurrentUserId(), fileMd5);
        return getSuccessResponseVO(task);
    }

    @AuditLog(value = AuditAction.EDIT_SUMMARY, targetType = TargetType.FILE,
              targetIdParam = "fileId")
    @PostMapping("/editSummary")
    public ResponseVO editSummary(@RequestParam String fileId, @RequestParam String summary) {
        checkDeptWriteAccess(fileId);
        String userId = getCurrentUserId();
        FileInfo file = fileInfoService.getFileInfoByFileId(fileId);
        if (file == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        // 摘要仅文件所有者（个人空间）或 admin/部门主管（部门空间）可编辑
        if (summary != null && summary.length() > 200) {
            throw new BusinessException("摘要不能超过200字");
        }
        FileInfo bean = new FileInfo();
        bean.setSummary(summary != null && !summary.isEmpty() ? summary : "");
        bean.setLastUpdateUserId(userId);
        if (file.getDepartmentId() != null && !file.getDepartmentId().isEmpty()) {
            fileInfoService.updateFileInfoByFileId(bean, fileId);
        } else {
            fileInfoService.updateFileInfoByFileIdAndUserId(bean, fileId, userId);
        }
        return getSuccessResponseVO(null);
    }

    @PostMapping("/aiSummary")
    public ResponseVO aiSummary(@RequestParam String fileId) {
        checkDeptWriteAccess(fileId);
        String summary = aiSummaryService.generateSummary(fileId);
        return getSuccessResponseVO(summary);
    }

    @GetMapping("/getImage/{imageFolder}/{imageName}")
    public void getImage(HttpServletResponse response,
                         @PathVariable String imageFolder, @PathVariable String imageName) {
        super.getImage(response, imageFolder, imageName);
    }

    @GetMapping("ts/getVideoInfo/{fileId}")
    public void getVideoInfo(HttpServletResponse response, @PathVariable String fileId) {
        super.getFile(response, fileId, getCurrentUserId());
    }

    @RequestMapping("/getFile/{fileId}")
    public void getFile(HttpServletResponse response, @PathVariable String fileId) {
        super.getFile(response, fileId, getCurrentUserId());
    }

    @PostMapping("/newFolder")
    public ResponseVO newFolder(@RequestParam String filePid, @RequestParam String fileName,
                                 @RequestParam(defaultValue = "false") Boolean deptMode,
                                 @RequestParam(required = false) String departmentId,
                                 @RequestParam(defaultValue = "false") Boolean autoRename) {
        SessionWebUserDto dto = new SessionWebUserDto();
        dto.setUserId(getCurrentUserId());
        if (departmentId != null && !departmentId.isEmpty()) {
            dto.setDepartmentId(departmentId);
        } else if (deptMode) {
            dto.setDepartmentId(getCurrentUserDepartmentId());
        }
        FileInfo fileInfo = fileInfoService.newFolder(filePid, getCurrentUserId(), fileName, dto.getDepartmentId(), autoRename);
        if (dto.getDepartmentId() != null) {
            FileInfo update = new FileInfo();
            update.setDepartmentId(dto.getDepartmentId());
            fileInfoService.updateFileInfoByFileIdAndUserId(update, fileInfo.getFileId(), getCurrentUserId());
        }
        // 重新查询以获取 JOIN 字段（nickName=上传者, departmentName）
        fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileInfo.getFileId(), getCurrentUserId());
        // 手动写审计日志（新建文件夹时 fileId 才生成，AOP 无法提前提取）
        writeCreateFolderAudit(fileInfo, dto.getDepartmentId());
        return getSuccessResponseVO(CopyTools.copy(fileInfo, FileInfoVO.class));
    }

    @RequestMapping("/getFolderInfo")
    public ResponseVO getFolderInfo(@RequestParam String path) {
        return super.getFolderInfo(path, getCurrentUserId());
    }

    /** 检测目标目录是否有同名文件 */
    @RequestMapping("/checkDuplicate")
    public ResponseVO checkDuplicate(@RequestParam String fileName,
                                      @RequestParam String filePid,
                                      @RequestParam(defaultValue = "false") Boolean deptMode,
                                      @RequestParam(required = false) String departmentId) {
        FileInfoQuery query = new FileInfoQuery();
        query.setFilePid(filePid);
        query.setFileName(fileName);
        query.setDelFlag(FileDelFlagEnum.USING.getFlag());
        query.setArchived(0);
        if (deptMode || (departmentId != null && !departmentId.isEmpty())) {
            String deptId = departmentId != null && !departmentId.isEmpty() ? departmentId : getCurrentUserDepartmentId();
            if (deptId != null) query.setDepartmentId(deptId);
        } else {
            query.setUserId(getCurrentUserId());
        }
        boolean duplicate = fileInfoMapper.selectCount(query) > 0;
        return getSuccessResponseVO(Map.of("duplicate", duplicate));
    }

    @PostMapping("/rename")
    @AuditLog(value = AuditAction.RENAME, targetType = TargetType.FILE,
              targetIdParam = "fileId", targetNameParam = "newName")
    public ResponseVO rename(@RequestParam String fileId, @RequestParam("fileName") String newName,
                              @RequestParam(defaultValue = "false") Boolean autoRename) {
        checkDeptWriteAccess(fileId);
        FileInfo fileInfo = fileInfoService.rename(fileId, getCurrentUserId(), newName, autoRename);
        // 重新查询以获取 JOIN 字段（nickName=上传者, lastUpdateUserNickName, departmentName）
        fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(fileId, fileInfo.getUserId());
        return getSuccessResponseVO(CopyTools.copy(fileInfo, FileInfoVO.class));
    }

    @RequestMapping("/loadAllFolder")
    public ResponseVO loadAllFolder(@RequestParam String filePid, @RequestParam("currentFileIds") String currentFolderIds,
                                     @RequestParam(defaultValue = "false") Boolean deptMode,
                                     @RequestParam(required = false) String departmentId) {
        FileInfoQuery query = new FileInfoQuery();
        if (!isAdmin() || departmentId == null || departmentId.isEmpty()) {
            query.setUserId(getCurrentUserId());
        }
        query.setFilePid(filePid);
        query.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        if (!StringTools.isEmpty(currentFolderIds)) {
            query.setExcludeFileIdArray(currentFolderIds.split(","));
        }
        query.setOrderBy("create_time desc");
        query.setDelFlag(FileDelFlagEnum.USING.getFlag());
        query.setArchived(0);
        query.setStatus(FileStatusEnum.USING.getStatus());
        query.setDeptMode(deptMode);
        if (deptMode) {
            query.setDeptMode(true);
            String deptId = (departmentId != null && !departmentId.isEmpty()) ? departmentId : getCurrentUserDepartmentId();
            if (deptId != null && !deptId.isEmpty()) {
                query.setDepartmentIds(java.util.List.of(deptId));
            }
        }
        List<FileInfo> folderList = fileInfoMapper.selectList(query);
        enrichNickNames(folderList);
        return getSuccessResponseVO(CopyTools.copyList(
                folderList, FileInfoVO.class));
    }

    @RequestMapping("/changeFileFolder")
    @AuditLog(value = AuditAction.MOVE, targetType = TargetType.FILE,
              targetIdParam = "fileIds")
    public ResponseVO changeFileFolder(@RequestParam String fileIds, @RequestParam String filePid) {
        checkDeptWriteAccess(fileIds.split(",")[0]);
        fileInfoService.changeFileFolder(getCurrentUserId(), fileIds, filePid, isAdmin());
        return getSuccessResponseVO(null);
    }

    @RequestMapping("/createDownloadUrl/{fileId}")
    public ResponseVO createDownloadUrl(@PathVariable String fileId) {
        return super.createDownloadUrl(fileId, getCurrentUserId());
    }

    @GetMapping("/download/{code}")
    public void download(HttpServletRequest request, HttpServletResponse response, @PathVariable String code) throws Exception {
        super.download(request, response, code);
    }

    @PostMapping("/delFile")
    @AuditLog(value = AuditAction.DELETE, targetType = TargetType.FILE,
              targetIdParam = "fileIds")
    public ResponseVO delFile(String fileIds) {
        checkDeptWriteAccess(fileIds.split(",")[0]);
        String userId = getCurrentUserId();
        if (isAdmin()) {
            fileInfoService.delFileBatch(null, fileIds, true);
        } else {
            fileInfoService.removeFile2RecycleBatch(userId, fileIds, false);
        }
        return getSuccessResponseVO(null);
    }


    /** 个人空间文件上传到部门空间 */
    @AuditLog(value = AuditAction.COPY_TO_DEPT, targetType = TargetType.FILE, targetIdParam = "fileId")
    @PostMapping("/copyToDept")
    public ResponseVO copyToDept(@RequestParam String fileId, @RequestParam String targetFolderId,
                                  @RequestParam String departmentId) {
        fileInfoService.copyFile(fileId, targetFolderId, getCurrentUserId(), departmentId);
        return getSuccessResponseVO(null);
    }

    /** 部门空间文件转存到个人空间 */
    @AuditLog(value = AuditAction.COPY_TO_PERSONAL, targetType = TargetType.FILE, targetIdParam = "fileId")
    @PostMapping("/copyToPersonal")
    public ResponseVO copyToPersonal(@RequestParam String fileId, @RequestParam String targetFolderId) {
        fileInfoService.copyFile(fileId, targetFolderId, getCurrentUserId(), null);
        return getSuccessResponseVO(null);
    }

    private void enrichNickNames(List<FileInfo> files) {
        if (files == null || files.isEmpty()) return;
        Set<String> userIds = new HashSet<>();
        for (FileInfo f : files) {
            if (f.getUserId() != null) userIds.add(f.getUserId());
            if (f.getLastUpdateUserId() != null) userIds.add(f.getLastUpdateUserId());
        }
        if (userIds.isEmpty()) return;
        try {
            Map<String, Map<String, Object>> userMap =
                authFeignClient.batchGetUserInfo(new ArrayList<>(userIds));
            for (FileInfo f : files) {
                Map<String, Object> u = userMap.get(f.getUserId());
                if (u != null) f.setNickName((String) u.get("nickName"));
                Map<String, Object> lu = userMap.get(f.getLastUpdateUserId());
                if (lu != null) f.setLastUpdateUserNickName((String) lu.get("nickName"));
            }
        } catch (Exception e) {
            // 昵称获取失败不影响主流程
        }
    }

    /** 部门空间写入权限：仅 admin / 部门主管可操作 */
    private void checkDeptWriteAccess(String fileId) {
        if (isAdmin()) return;
        FileInfo file = fileInfoService.getFileInfoByFileId(fileId);
        if (file == null) throw new BusinessException(ResponseCodeEnum.CODE_600);
        if (file.getDepartmentId() != null
                && !departmentService.isDeptHead(file.getDepartmentId(), getCurrentUserId())) {
            throw new BusinessException("无权操作部门文件");
        }
    }

    /** 部门模式写入权限（用于有 deptMode 参数的端点） */
    private void checkDeptModeWriteAccess(boolean deptMode, String departmentId) {
        if (!deptMode) return;
        if (isAdmin()) return;
        String deptId = departmentId != null && !departmentId.isEmpty()
                ? departmentId : getCurrentUserDepartmentId();
        if (deptId == null || !departmentService.isDeptHead(deptId, getCurrentUserId())) {
            throw new BusinessException("无权操作部门文件");
        }
    }

    private void writeCreateFolderAudit(FileInfo folder, String departmentId) {
        try {
            String userName = "";
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String encodedName = attrs.getRequest().getHeader("X-User-NickName");
                if (encodedName != null) {
                    userName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
                }
            }
            auditLogService.log(folder.getUserId(), userName, "CREATE_FOLDER", "FOLDER",
                    folder.getFileId(), folder.getFileName(), departmentId);
        } catch (Exception e) {
            // 审计写入失败不阻塞主流程
        }
    }
}
