package com.smartdrive.file.service.impl;

import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.dto.DownloadFileDto;
import com.smartdrive.common.dto.SessionWebUserDto;
import com.smartdrive.common.dto.UploadResultDto;
import com.smartdrive.common.dto.UploadTaskDto;
import com.smartdrive.common.dto.UserSpaceDto;
import com.smartdrive.common.enums.*;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.query.SimplePage;
import com.smartdrive.common.utils.DateUtils;
import com.smartdrive.common.utils.ProcessUtils;
import com.smartdrive.common.utils.ScaleFilter;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.file.component.FileRedisComponent;
import com.smartdrive.file.config.FileAppConfig;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.entity.query.FileInfoQuery;
import com.smartdrive.file.mapper.FileInfoMapper;
import com.smartdrive.file.service.AuditLogService;
import com.smartdrive.file.service.DepartmentService;
import com.smartdrive.file.service.FileInfoService;
import com.smartdrive.file.service.FileSearchService;
import com.smartdrive.file.elasticsearch.FileDocument;
import com.smartdrive.file.feign.AuthFeignClient;
import jakarta.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FileInfoServiceImpl implements FileInfoService {

    @Resource
    private FileInfoMapper fileInfoMapper;
    @Resource
    private AuthFeignClient authFeignClient;
    private final FileRedisComponent redisComponent;
    private final FileAppConfig appConfig;

    @Lazy
    @Resource
    private FileInfoServiceImpl fileInfoServiceImpl;

    private final DepartmentService departmentService;
    private final AuditLogService auditLogService;
    private final FileSearchService fileSearchService;

    private static final Logger logger = LoggerFactory.getLogger(FileInfoServiceImpl.class);

    public FileInfoServiceImpl(FileRedisComponent redisComponent, FileAppConfig appConfig,
                               DepartmentService departmentService,
                               AuditLogService auditLogService,
                               FileSearchService fileSearchService) {
        this.redisComponent = redisComponent;
        this.appConfig = appConfig;
        this.departmentService = departmentService;
        this.auditLogService = auditLogService;
        this.fileSearchService = fileSearchService;
    }

    @Override
    public List<FileInfo> findListByParam(FileInfoQuery query) { return fileInfoMapper.selectList(query); }

    @Override
    public Integer findCountByParam(FileInfoQuery query) { return fileInfoMapper.selectCount(query); }

    @Override
    public PaginationResultVO<FileInfo> findListByPage(FileInfoQuery query) {
        Integer count = findCountByParam(query);
        Integer pageSize = query.getPageSize() == null ? PageSize.SIZE15.getSize() : query.getPageSize();
        SimplePage page = new SimplePage(query.getPageNo(), count, pageSize);
        query.setSimplePage(page);
        List<FileInfo> list = findListByParam(query);
        enrichNickNames(list);
        return new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    @Override
    public Integer add(FileInfo bean) { return fileInfoMapper.insert(bean); }

    @Override
    public Integer addBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) return 0;
        return fileInfoMapper.insertBatch(listBean);
    }

    @Override
    public Integer addOrUpdateBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) return 0;
        return fileInfoMapper.insertOrUpdateBatch(listBean);
    }

    @Override
    public FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId) {
        FileInfo file = fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
        if (file != null) { enrichNickNames(List.of(file)); }
        return file;
    }

    @Override
    public Long getUsedSpace(String userId) {
        return fileInfoMapper.selectUseSpaceByUserId(userId);
    }

    /** 通过 Feign 批量获取用户昵称，填充到 FileInfo 列表中 */
    private void enrichNickNames(List<FileInfo> files) {
        if (files == null || files.isEmpty()) return;
        Set<String> userIds = new HashSet<>();
        for (FileInfo f : files) {
            if (f.getUserId() != null) userIds.add(f.getUserId());
            if (f.getLastUpdateUserId() != null) userIds.add(f.getLastUpdateUserId());
        }
        if (userIds.isEmpty()) return;
        try {
            Map<String, Map<String, Object>> userMap = authFeignClient.batchGetUserInfo(new ArrayList<>(userIds));
            for (FileInfo f : files) {
                Map<String, Object> u = userMap.get(f.getUserId());
                if (u != null) f.setNickName((String) u.get("nickName"));
                Map<String, Object> lu = userMap.get(f.getLastUpdateUserId());
                if (lu != null) f.setLastUpdateUserNickName((String) lu.get("nickName"));
            }
        } catch (Exception e) {
            logger.warn("Feign batchGetUserInfo failed: {}", e.getMessage());
        }
    }

    @Override
    public FileInfo getFileInfoByFileId(String fileId) {
        return fileInfoMapper.selectByFileId(fileId);
    }

    @Override
    public Integer updateFileInfoByFileIdAndUserId(FileInfo bean, String fileId, String userId) {
        return fileInfoMapper.updateByFileIdAndUserId(bean, fileId, userId);
    }

    @Override
    public Integer updateFileInfoByFileId(FileInfo bean, String fileId) {
        return fileInfoMapper.updateByFileId(bean, fileId);
    }

    @Override
    public Integer deleteFileInfoByFileIdAndUserId(String fileId, String userId) {
        return fileInfoMapper.deleteByFileIdAndUserId(fileId, userId);
    }

    // ===================================================================
    // 核心算法：文件上传（含分片、秒传） — 完整保留
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto uploadFile(SessionWebUserDto sessionWebUserDto, String fileId, MultipartFile file,
                                       String fileName, String filePid, String fileMd5, Integer chunkIndex, Integer chunks) {
        UploadResultDto resultDto = new UploadResultDto();
        File tempFileFolder = null;
        Boolean uploadSuccess = true;
        try {
            if (StringTools.isEmpty(fileId)) {
                fileId = StringTools.getRandomString(Constants.LENGTH_10);
            }
            resultDto.setFileId(fileId);
            Date curDate = new Date();
            UserSpaceDto userSpaceDto = redisComponent.getUserSpace(sessionWebUserDto.getUserId());

            // 秒传逻辑
            if (chunkIndex == 0) {
                FileInfoQuery fileInfoQuery = new FileInfoQuery();
                fileInfoQuery.setFileMd5(fileMd5);
                fileInfoQuery.setSimplePage(new SimplePage(0, 1));
                fileInfoQuery.setStatus(FileStatusEnum.USING.getStatus());
                List<FileInfo> fileList = fileInfoMapper.selectList(fileInfoQuery);

                if (!fileList.isEmpty()) {
                    FileInfo existing = fileList.get(0);
                    if (existing.getFileSize() + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
                        throw new BusinessException(ResponseCodeEnum.CODE_904);
                    }
                    existing.setFileId(fileId);
                    existing.setFilePid(filePid);
                    existing.setUserId(sessionWebUserDto.getUserId());
                    existing.setCreateTime(curDate);
                    existing.setLastUpdateTime(curDate);
                    existing.setDelFlag(FileDelFlagEnum.USING.getFlag());
                    existing.setArchived(0);
                    existing.setArchivedTime(null);
                    existing.setDepartmentId(sessionWebUserDto.getDepartmentId());
                    existing.setSummary(sessionWebUserDto.getSummary());
                    fileName = autoRename(filePid, sessionWebUserDto.getUserId(), fileName, sessionWebUserDto.getDepartmentId());
                    existing.setFileName(fileName);
                    fileInfoMapper.insert(existing);
                    updateUserSpace(sessionWebUserDto, existing.getFileSize());
                    resultDto.setStatus(UploadStatusEnum.UPLOAD_SECONDS.getCode());
                    writeUploadAudit(sessionWebUserDto, fileId, fileName);
                    fileSearchService.sync(FileDocument.from(existing));
                    return resultDto;
                }
            }

            // 校验空间
            Long currentTempSize = redisComponent.getFileTempSize(sessionWebUserDto.getUserId(), fileId);
            if (file.getSize() + currentTempSize + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.CODE_904);
            }

            // 创建临时分片目录
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = sessionWebUserDto.getUserId() + fileId;
            tempFileFolder = new File(tempFolderName + "/" + currentUserFolderName);
            if (!tempFileFolder.exists()) { tempFileFolder.mkdirs(); }

            // 写入当前分片
            File newFile = new File(tempFileFolder.getPath() + "/" + chunkIndex);
            file.transferTo(newFile);
            redisComponent.saveFileTempSize(sessionWebUserDto.getUserId(), fileId, file.getSize());

            // 保存上传任务，供前端刷新后通过 /upload/resume 恢复
            UploadTaskDto task = new UploadTaskDto();
            task.setFileId(fileId);
            task.setFileName(fileName);
            task.setFileMd5(fileMd5);
            task.setChunks(chunks);
            task.setCompletedChunks(chunkIndex + 1);
            redisComponent.saveUploadTask(sessionWebUserDto.getUserId(), task);

            if (chunkIndex < chunks - 1) {
                resultDto.setStatus(UploadStatusEnum.UPLOADING.getCode());
                return resultDto;
            }

            // 最后一个分片：执行合并
            String month = DateUtils.format(new Date(), DateTimePatternEnum.YYYYMM.getPattern());
            String fileSuffix = StringTools.getFileSuffix(fileName);
            String realFileName = currentUserFolderName + fileSuffix;
            FileTypeEnum fileTypeEnum = FileTypeEnum.getFileTypeBySuffix(fileSuffix);
            fileName = autoRename(filePid, sessionWebUserDto.getUserId(), fileName, sessionWebUserDto.getDepartmentId());

            FileInfo fileInfo = new FileInfo();
            fileInfo.setFileId(fileId);
            fileInfo.setUserId(sessionWebUserDto.getUserId());
            fileInfo.setFileMd5(fileMd5);
            fileInfo.setFileName(fileName);
            fileInfo.setFilePath(month + "/" + realFileName);
            fileInfo.setFilePid(filePid);
            fileInfo.setCreateTime(curDate);
            fileInfo.setLastUpdateTime(curDate);
            fileInfo.setLastUpdateUserId(sessionWebUserDto.getUserId());
            fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
            fileInfo.setFileType(fileTypeEnum.getType());
            fileInfo.setStatus(FileStatusEnum.TRANSCODING.getStatus());
            fileInfo.setFolderType(FileFolderTypeEnum.FILE.getType());
            fileInfo.setDelFlag(FileDelFlagEnum.USING.getFlag());
            fileInfo.setDepartmentId(sessionWebUserDto.getDepartmentId());
            if (sessionWebUserDto.getSummary() != null && !sessionWebUserDto.getSummary().isEmpty()) {
                fileInfo.setSummary(sessionWebUserDto.getSummary());
            }

            fileInfoMapper.insert(fileInfo);
            Long totalSize = redisComponent.getFileTempSize(sessionWebUserDto.getUserId(), fileId);
            updateUserSpace(sessionWebUserDto, totalSize);
            resultDto.setStatus(UploadStatusEnum.UPLOAD_FINISH.getCode());

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileInfoServiceImpl.transcodeFile(fileInfo.getFileId(), sessionWebUserDto);
                }
            });
            writeUploadAudit(sessionWebUserDto, fileId, fileName);
            fileSearchService.sync(FileDocument.from(fileInfo));
            redisComponent.deleteUploadTask(sessionWebUserDto.getUserId(), fileMd5);
            return resultDto;
        } catch (BusinessException e) {
            logger.error("文件上传失败", e);
            uploadSuccess = false;
            throw e;
        } catch (Exception e) {
            logger.error("文件上传失败", e);
            uploadSuccess = false;
        } finally {
            if (!uploadSuccess && tempFileFolder != null) {
                try { FileUtils.deleteDirectory(tempFileFolder); } catch (IOException e) { logger.error("删除临时目录失败", e); }
            }
        }
        return resultDto;
    }

    // ===================================================================
    // 核心算法：新建文件夹 — 完整保留
    // ===================================================================
    @Override
    public FileInfo newFolder(String filePid, String userId, String folderName, String departmentId, boolean autoRename) {
        if (autoRename) {
            folderName = autoRename(filePid, userId, folderName, departmentId);
        } else {
            checkFileName(filePid, userId, folderName, FileFolderTypeEnum.FOLDER.getType(), departmentId, null);
        }
        Date curDate = new Date();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFilePid(filePid);
        fileInfo.setUserId(userId);
        fileInfo.setFileId(StringTools.getRandomString(Constants.LENGTH_10));
        fileInfo.setFileName(folderName);
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        fileInfo.setDepartmentId(departmentId);
        fileInfo.setStatus(FileStatusEnum.USING.getStatus());
        fileInfo.setDelFlag(FileDelFlagEnum.USING.getFlag());
        fileInfoMapper.insert(fileInfo);
        fileSearchService.sync(FileDocument.from(fileInfo));
        return fileInfo;
    }

    // ===================================================================
    // 核心算法：重命名 — 完整保留
    // ===================================================================
    @Override
    public FileInfo rename(String fileId, String userId, String newName, boolean autoRename) {
        FileInfo fileInfo = fileInfoMapper.selectByFileId(fileId);
        if (fileInfo == null) { throw new BusinessException("文件不存在"); }
        if (fileInfo.getArchived() != null && fileInfo.getArchived() == 1) {
            throw new BusinessException("归档文件不可重命名");
        }
        requireWritePermission(fileId, userId);
        String filePid = fileInfo.getFilePid();
        if (fileInfo.getFolderType().equals(FileFolderTypeEnum.FILE.getType())) {
            newName = newName + StringTools.getFileSuffix(fileInfo.getFileName());
        }
        if (autoRename) {
            newName = autoRename(filePid, userId, newName, fileInfo.getDepartmentId());
        } else {
            checkFileName(filePid, userId, newName, fileInfo.getFolderType(), fileInfo.getDepartmentId(), fileId);
        }
        Date curDate = new Date();
        FileInfo updateFileInfo = new FileInfo();
        updateFileInfo.setFileName(newName);
        updateFileInfo.setLastUpdateTime(curDate);
        updateFileInfo.setLastUpdateUserId(userId);
        fileInfoMapper.updateByFileIdAndUserId(updateFileInfo, fileId, fileInfo.getUserId());
        fileSearchService.sync(FileDocument.from(fileInfoMapper.selectByFileId(fileId)));
        fileInfo.setFileName(newName);
        fileInfo.setLastUpdateTime(curDate);
        return fileInfo;
    }

    // ===================================================================
    // 核心算法：加载所有文件夹 — 完整保留
    // ===================================================================
    @Override
    public List<FileInfo> loadAllFolder(String userId, String filePid, String currentFolderIds) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        if (!StringTools.isEmpty(currentFolderIds)) {
            fileInfoQuery.setExcludeFileIdArray(currentFolderIds.split(","));
        }
        fileInfoQuery.setOrderBy("create_time desc");
        fileInfoQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        fileInfoQuery.setStatus(FileStatusEnum.USING.getStatus());
        return fileInfoMapper.selectList(fileInfoQuery);
    }

    // ===================================================================
    // 核心算法：移动文件/文件夹 — 完整保留
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeFileFolder(String userId, String fileIds, String filePid, boolean isAdmin) {
        if (fileIds.equals(filePid)) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        // 目标文件夹权限校验
        if (!Constants.ZERO_STR.equals(filePid)) {
            FileInfo destFolder = fileInfoMapper.selectByFileId(filePid);
            if (destFolder == null || !destFolder.getDelFlag().equals(FileDelFlagEnum.USING.getFlag())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
            checkMovePermission(destFolder, userId, isAdmin);
        }

        String[] fileIdArray = fileIds.split(",");
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFileIdArray(fileIdArray);
        List<FileInfo> selectedFileInfoList = fileInfoMapper.selectList(fileInfoQuery);

        // 权限校验
        for (FileInfo item : selectedFileInfoList) {
            checkMovePermission(item, userId, isAdmin);
        }

        // 目标文件夹内容（查重命名用，按源文件所属空间过滤：部门空间按 departmentId，个人空间按 userId）
        fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        fileInfoQuery.setArchived(0);
        if (!selectedFileInfoList.isEmpty()) {
            FileInfo first = selectedFileInfoList.get(0);
            if (first.getDepartmentId() != null) {
                fileInfoQuery.setDepartmentId(first.getDepartmentId());
            } else {
                fileInfoQuery.setUserId(userId);
            }
        }
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(fileInfoQuery);
        Map<String, FileInfo> fileInfoMap = fileInfoList.stream()
                .collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (o1, o2) -> o1));

        for (FileInfo item : selectedFileInfoList) {
            FileInfo rootFileInfo = fileInfoMap.get(item.getFileName());
            FileInfo updateFileInfo = new FileInfo();
            updateFileInfo.setFilePid(filePid);
            updateFileInfo.setLastUpdateTime(new Date());
            updateFileInfo.setLastUpdateUserId(userId);
            if (rootFileInfo != null) {
                updateFileInfo.setFileName(StringTools.rename(item.getFileName()));
            }
            fileInfoMapper.updateByFileIdAndUserId(updateFileInfo, item.getFileId(), item.getUserId());
        }
    }

    // ===================================================================
    // 核心算法：移动到回收站 — 完整保留
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFile2RecycleBatch(String userId, String fileIds, boolean isAdmin) {
        String[] fileIdArray = fileIds.split(",");
        FileInfoQuery query = new FileInfoQuery();
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDelFlagEnum.USING.getFlag());
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);
        if (fileInfoList.isEmpty()) { return; }

        // 权限校验
        for (FileInfo item : fileInfoList) {
            checkDeletePermission(item, userId, isAdmin);
        }

        List<String> delSubFileIdList = new ArrayList<>();
        for (FileInfo item : fileInfoList) {
            if (item.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
                findAllSubFolderFileList(delSubFileIdList, item.getFileId(), FileDelFlagEnum.USING.getFlag());
            }
        }

        if (!delSubFileIdList.isEmpty()) {
            FileInfo updateFileInfo = new FileInfo();
            updateFileInfo.setDelFlag(FileDelFlagEnum.RECYCLE.getFlag());
            updateFileInfo.setRecycleTime(new Date());
            updateFileInfo.setLastUpdateTime(new Date());
            // 子文件按 file_id 精确更新，不限 user_id（部门文件夹内含其他成员上传的文件）
            fileInfoMapper.updateFileDelFlagByIds(updateFileInfo, delSubFileIdList, FileDelFlagEnum.USING.getFlag());
        }

        // 顶层文件/文件夹：进回收站 + 移到根目录
        for (String fileId : fileIdArray) {
            for (FileInfo item : fileInfoList) {
                if (item.getFileId().equals(fileId)) {
                    FileInfo updateInfo = new FileInfo();
                    updateInfo.setRecycleTime(new Date());
                    updateInfo.setLastUpdateTime(new Date());
                    updateInfo.setDelFlag(FileDelFlagEnum.RECYCLE.getFlag());
                    updateInfo.setFilePid(Constants.ZERO_STR);
                    fileInfoMapper.updateFileDelFlagBatch(updateInfo, item.getUserId(), null, List.of(fileId), FileDelFlagEnum.USING.getFlag());
                    break;
                }
            }
        }
        for (String id : fileIdArray) {
            FileInfo f = fileInfoMapper.selectByFileId(id);
            if (f != null) fileSearchService.sync(FileDocument.from(f));
        }
    }

    // ===================================================================
    // 核心算法：回收站恢复 — 直接按原 filePid 恢复（删除时已处理归档文件）
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFileBatch(String userId, String fileIds) {
        String[] fileIdArray = fileIds.split(",");
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(FileDelFlagEnum.RECYCLE.getFlag());
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);

        List<String> recSubFileIdList = new ArrayList<>();
        for (FileInfo item : fileInfoList) {
            if (item.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
                findAllSubFolderFileList(recSubFileIdList, item.getFileId(), FileDelFlagEnum.RECYCLE.getFlag());
            }
        }

        if (!recSubFileIdList.isEmpty()) {
            FileInfo updateFileInfo = new FileInfo();
            updateFileInfo.setDelFlag(FileDelFlagEnum.USING.getFlag());
            updateFileInfo.setLastUpdateTime(new Date());
            updateFileInfo.setLastUpdateUserId(userId);
            fileInfoMapper.updateFileDelFlagByIds(updateFileInfo, recSubFileIdList, FileDelFlagEnum.RECYCLE.getFlag());
        }

        // 同名冲突检测（恢复位置即原 filePid，部门空间按 departmentId 查，个人空间按 userId 查）
        for (FileInfo item : fileInfoList) {
            FileInfoQuery dupQuery = new FileInfoQuery();
            dupQuery.setFilePid(item.getFilePid());
            dupQuery.setFileName(item.getFileName());
            dupQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
            if (item.getDepartmentId() != null) {
                dupQuery.setDepartmentId(item.getDepartmentId());
            } else {
                dupQuery.setUserId(userId);
            }
            if (fileInfoMapper.selectCount(dupQuery) > 0) {
                FileInfo renameInfo = new FileInfo();
                renameInfo.setFileName(StringTools.rename(item.getFileName()));
                fileInfoMapper.updateByFileIdAndUserId(renameInfo, item.getFileId(), item.getUserId());
            }
        }

        // 恢复顶层文件的 delFlag
        FileInfo updateFileInfo = new FileInfo();
        updateFileInfo.setDelFlag(FileDelFlagEnum.USING.getFlag());
        updateFileInfo.setLastUpdateTime(new Date());
        updateFileInfo.setLastUpdateUserId(userId);
        fileInfoMapper.updateFileDelFlagByIds(updateFileInfo, Arrays.asList(fileIdArray), FileDelFlagEnum.RECYCLE.getFlag());

        for (String id : fileIdArray) {
            FileInfo f = fileInfoMapper.selectByFileId(id);
            if (f != null) fileSearchService.sync(FileDocument.from(f));
        }
    }

    // ===================================================================
    // 部门主管：按文件ID恢复/彻底删除（不限userId）
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFileById(String userId, String fileId) {
        FileInfo file = fileInfoMapper.selectByFileId(fileId);
        if (file == null || !FileDelFlagEnum.RECYCLE.getFlag().equals(file.getDelFlag())) {
            throw new BusinessException("文件不存在或不在回收站");
        }
        checkDeletePermission(file, userId, false); // 需要是部门主管

        // 同名冲突检测：恢复位置即原 filePid，部门空间按 departmentId 查，个人空间按 userId 查
        FileInfoQuery dupQuery = new FileInfoQuery();
        dupQuery.setFilePid(file.getFilePid());
        dupQuery.setFileName(file.getFileName());
        dupQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        if (file.getDepartmentId() != null) {
            dupQuery.setDepartmentId(file.getDepartmentId());
        } else {
            dupQuery.setUserId(file.getUserId());
        }
        if (fileInfoMapper.selectCount(dupQuery) > 0) {
            FileInfo renameInfo = new FileInfo();
            renameInfo.setFileName(StringTools.rename(file.getFileName()));
            fileInfoMapper.updateByFileId(renameInfo, file.getFileId());
        }

        // 恢复子文件（文件夹递归）
        List<String> recSubFileIdList = new ArrayList<>();
        if (FileFolderTypeEnum.FOLDER.getType().equals(file.getFolderType())) {
            findAllSubFolderFileList(recSubFileIdList, fileId, FileDelFlagEnum.RECYCLE.getFlag());
        }
        if (!recSubFileIdList.isEmpty()) {
            FileInfo subUpdate = new FileInfo();
            subUpdate.setDelFlag(FileDelFlagEnum.USING.getFlag());
            subUpdate.setLastUpdateTime(new Date());
            fileInfoMapper.updateFileDelFlagByIds(subUpdate, recSubFileIdList, FileDelFlagEnum.RECYCLE.getFlag());
        }
        FileInfo update = new FileInfo();
        update.setDelFlag(FileDelFlagEnum.USING.getFlag());
        update.setLastUpdateTime(new Date());
        fileInfoMapper.updateFileDelFlagByIds(update, List.of(fileId), FileDelFlagEnum.RECYCLE.getFlag());
        FileInfo f = fileInfoMapper.selectByFileId(fileId);
        if (f != null) fileSearchService.sync(FileDocument.from(f));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFileById(String userId, String fileId) {
        FileInfo file = fileInfoMapper.selectByFileId(fileId);
        if (file == null) throw new BusinessException("文件不存在");
        checkDeletePermission(file, userId, false); // 需要是部门主管
        // userId=null → 不限制上传者；admin=false → 查 delFlag=RECYCLE
        delFileBatch(null, fileId, false);
    }

    // =================================================================
    // 核心算法：彻底删除 — 完整保留
    // ================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFileBatch(String userId, String fileIds, Boolean admin) {
        String[] fileIdArray = fileIds.split(",");
        FileInfoQuery query = new FileInfoQuery();
        if (!admin) {
            query.setUserId(userId);
        }
        query.setFileIdArray(fileIdArray);
        query.setDelFlag(admin ? FileDelFlagEnum.USING.getFlag() : FileDelFlagEnum.RECYCLE.getFlag());
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);

        List<String> delSubFileIdList = new ArrayList<>();
        for (FileInfo item : fileInfoList) {
            if (item.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
                findAllSubFolderFileList(delSubFileIdList, item.getFileId(),
                        admin ? FileDelFlagEnum.USING.getFlag() : FileDelFlagEnum.RECYCLE.getFlag());
            }
        }

        // 收集物理文件路径（DB 删除前收集，删完再查引用数决定是否删物理文件）
        Set<String> filePaths = new HashSet<>();
        Set<String> fileCovers = new HashSet<>();
        for (FileInfo item : fileInfoList) {
            if (item.getFilePath() != null) filePaths.add(item.getFilePath());
            if (item.getFileCover() != null) fileCovers.add(item.getFileCover());
        }
        if (!delSubFileIdList.isEmpty()) {
            FileInfoQuery subQuery = new FileInfoQuery();
            subQuery.setFileIdArray(delSubFileIdList.toArray(new String[0]));
            for (FileInfo item : fileInfoMapper.selectList(subQuery)) {
                if (item.getFilePath() != null) filePaths.add(item.getFilePath());
                if (item.getFileCover() != null) fileCovers.add(item.getFileCover());
            }
        }

        if (!delSubFileIdList.isEmpty()) {
            // 子文件按 file_id 精确删除，不限 user_id（列表来自已过权限校验的顶层文件夹树）
            fileInfoMapper.deleteFileBatchWithOldDelFlag(null, null, delSubFileIdList,
                    admin ? null : FileDelFlagEnum.RECYCLE.getFlag());
        }

        List<String> delFileIdList = Arrays.asList(fileIdArray);
        fileInfoMapper.deleteFileBatchWithOldDelFlag(userId, null, delFileIdList,
                admin ? null : FileDelFlagEnum.RECYCLE.getFlag());

        // ES 物理删除
        for (String id : fileIdArray) {
            fileSearchService.delete(id);
        }

        // 清理物理文件：每个 file_path 无其他 USING 记录引用时才删除
        String fileFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
        for (String filePath : filePaths) {
            if (fileInfoMapper.countByFilePath(filePath, FileDelFlagEnum.USING.getFlag()) == 0) {
                File f = new File(fileFolder + "/" + filePath);
                if (f.exists() && !f.delete()) {
                    logger.warn("物理文件删除失败: {}", f.getAbsolutePath());
                }
                // 视频 HLS 分片目录：主文件名去掉扩展名即为 ts 目录
                int dotIdx = filePath.lastIndexOf('.');
                if (dotIdx > 0) {
                    File tsDir = new File(fileFolder + "/" + filePath.substring(0, dotIdx));
                    if (tsDir.exists() && tsDir.isDirectory()) {
                        try { org.apache.commons.io.FileUtils.deleteDirectory(tsDir); }
                        catch (IOException e) { logger.warn("视频分片目录删除失败: {}", tsDir.getAbsolutePath()); }
                    }
                }
            }
        }
        // 清理缩略图
        for (String cover : fileCovers) {
            if (fileInfoMapper.countByFileCover(cover, FileDelFlagEnum.USING.getFlag()) == 0) {
                File f = new File(fileFolder + "/" + cover);
                if (f.exists() && !f.delete()) {
                    logger.warn("缩略图删除失败: {}", f.getAbsolutePath());
                }
            }
        }

        // 更新各上传者的已用空间（Redis + DB 同步）
        Set<String> affectedUsers = new HashSet<>();
        for (FileInfo item : fileInfoList) {
            if (item.getUserId() != null) affectedUsers.add(item.getUserId());
        }
        if (!delSubFileIdList.isEmpty()) {
            FileInfoQuery subQuery = new FileInfoQuery();
            subQuery.setFileIdArray(delSubFileIdList.toArray(new String[0]));
            for (FileInfo item : fileInfoMapper.selectList(subQuery)) {
                if (item.getUserId() != null) affectedUsers.add(item.getUserId());
            }
        }
        for (String uid : affectedUsers) {
            Long useSpace = fileInfoMapper.selectUseSpaceByUserId(uid);
            UserSpaceDto spaceDto = redisComponent.getUserSpace(uid);
            if (spaceDto != null) {
                spaceDto.setUseSpace(useSpace);
                redisComponent.saveUserSpace(uid, spaceDto);
            }
            try { authFeignClient.updateUserSpace(uid, useSpace); } catch (Exception e) { logger.warn("updateUserSpace failed: {}", e.getMessage()); }
        }
    }

    // ===================================================================
    // 核心算法：校验子目录合法性 — 完整保留
    // ===================================================================
    @Override
    public void checkRootFilePid(String rootFilePid, String userId, String fileId) {
        if (StringTools.isEmpty(fileId)) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        if (rootFilePid.equals(fileId)) { return; }
        checkFilePid(rootFilePid, userId, fileId);
    }

    private void checkFilePid(String rootFilePid, String userId, String fileId) {
        FileInfo fileInfo = fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        if (Constants.ZERO_STR.equals(fileInfo.getFilePid())) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        if (fileInfo.getFilePid().equals(rootFilePid)) { return; }
        checkFilePid(rootFilePid, userId, fileInfo.getFilePid());
    }

    // ===================================================================
    // 核心算法：转存分享文件 — 完整保留
    // ===================================================================
    @Override
    public void saveShare(String shareRootFileId, String shareFileIds, String myFolderId,
                          String shareUserId, String targetDepartmentId, String currentUserId) {
        boolean toDept = targetDepartmentId != null && !targetDepartmentId.isEmpty();
        String[] shareFileIdArray = shareFileIds.split(",");
        // 目标目录同名查重：个人空间按 userId，部门空间按 departmentId
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        if (toDept) {
            fileInfoQuery.setDeptMode(true);
            fileInfoQuery.setDepartmentIds(List.of(targetDepartmentId));
        } else {
            fileInfoQuery.setUserId(currentUserId);
            fileInfoQuery.setDepartmentIdIsNull(true);
        }
        fileInfoQuery.setFilePid(myFolderId);
        fileInfoQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        fileInfoQuery.setArchived(0);
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(fileInfoQuery);
        Map<String, FileInfo> fileInfoMap = fileInfoList.stream()
                .collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (o1, o2) -> o1));

        for (String fileId : shareFileIdArray) {
            checkRootFilePid(shareRootFileId, shareUserId, fileId);
        }

        fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFileIdArray(shareFileIdArray);
        List<FileInfo> shareFileInfoList = fileInfoMapper.selectList(fileInfoQuery);

        List<FileInfo> copyFileList = new ArrayList<>();
        Date curDate = new Date();
        for (FileInfo item : shareFileInfoList) {
            FileInfo existing = fileInfoMap.get(item.getFileName());
            if (existing != null) { item.setFileName(StringTools.rename(item.getFileName())); }
            findAllSubFolderFileListCopy(copyFileList, item, shareUserId, currentUserId, curDate, myFolderId);
        }
        // 统一设置目标部门：个人空间为 NULL，部门空间为主管所在部门（覆盖源文件可能携带的部门ID）
        for (FileInfo item : copyFileList) {
            item.setDepartmentId(toDept ? targetDepartmentId : null);
        }

        // 计算大小并验证空间
        long fileSizeSum = 0L;
        for (FileInfo item : copyFileList) {
            if (item.getFolderType().equals(FileFolderTypeEnum.FILE.getType())) { fileSizeSum += item.getFileSize(); }
        }
        UserSpaceDto userSpaceDto = redisComponent.getUserSpace(currentUserId);
        if (userSpaceDto != null && fileSizeSum + userSpaceDto.getUseSpace() > userSpaceDto.getTotalSpace()) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }

        fileInfoMapper.insertBatch(copyFileList);

        // 同步 ES
        for (FileInfo item : copyFileList) {
            fileSearchService.sync(FileDocument.from(item));
        }

        // 转存后同步已用空间（Redis + DB 重算，与 delFileBatch 一致）
        Long useSpace = fileInfoMapper.selectUseSpaceByUserId(currentUserId);
        if (userSpaceDto != null) {
            userSpaceDto.setUseSpace(useSpace);
            redisComponent.saveUserSpace(currentUserId, userSpaceDto);
        }
        try { authFeignClient.updateUserSpace(currentUserId, useSpace); } catch (Exception e) { logger.warn("updateUserSpace failed: {}", e.getMessage()); }
    }

    // ===================================================================
    // 核心算法：递归查找子文件列表 — 完整保留
    // ===================================================================
    private void findAllSubFolderFileList(List<String> fileIdList, String fileId, Integer delFlag) {
        FileInfoQuery query = new FileInfoQuery();
        // 按树递归收集，不按 user_id 过滤：部门文件夹内可能存在其他成员上传的子文件
        query.setFilePid(fileId);
        query.setDelFlag(delFlag);
        List<FileInfo> fileInfoList = fileInfoMapper.selectList(query);
        if (fileInfoList.isEmpty()) { return; }
        for (FileInfo item : fileInfoList) {
            fileIdList.add(item.getFileId());
            if (item.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
                findAllSubFolderFileList(fileIdList, item.getFileId(), delFlag);
            }
        }
    }

    // ===================================================================
    // 核心算法：递归拷贝文件树 — 完整保留
    // ===================================================================
    private void findAllSubFolderFileListCopy(List<FileInfo> copyList, FileInfo fileInfo,
                                               String sourceUserId, String currentUserId,
                                               Date curDate, String newFilePid) {
        String sourceFileId = fileInfo.getFileId();
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(null);
        fileInfo.setFilePid(newFilePid);
        fileInfo.setUserId(currentUserId);
        String newFileId = StringTools.getRandomString(Constants.LENGTH_10);
        fileInfo.setFileId(newFileId);
        copyList.add(fileInfo);

        if (fileInfo.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
            FileInfoQuery fileInfoQuery = new FileInfoQuery();
            fileInfoQuery.setFilePid(sourceFileId);
            List<FileInfo> sourceFileInfoList = fileInfoMapper.selectList(fileInfoQuery);
            for (FileInfo item : sourceFileInfoList) {
                findAllSubFolderFileListCopy(copyList, item, sourceUserId, currentUserId, curDate, newFileId);
            }
        }
    }

    /** 同 findAllSubFolderFileListCopy 但不按 userId 过滤（用于部门空间拷贝，子文件可能属于不同上传者） */
    private void collectAllSubFiles(List<FileInfo> copyList, FileInfo fileInfo,
                                     String currentUserId, Date curDate, String newFilePid) {
        String sourceFileId = fileInfo.getFileId();
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setFilePid(newFilePid);
        fileInfo.setUserId(currentUserId);
        String newFileId = StringTools.getRandomString(Constants.LENGTH_10);
        fileInfo.setFileId(newFileId);
        copyList.add(fileInfo);

        if (fileInfo.getFolderType() != null && fileInfo.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
            FileInfoQuery subQuery = new FileInfoQuery();
            subQuery.setFilePid(sourceFileId);
            subQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
            for (FileInfo item : fileInfoMapper.selectList(subQuery)) {
                collectAllSubFiles(copyList, item, currentUserId, curDate, newFileId);
            }
        }
    }

    // ===================================================================
    // 辅助方法 — 完整保留
    // ===================================================================
    private void checkFileName(String filePid, String userId, String fileName,
                               Integer folderType, String departmentId, String excludeFileId) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setFolderType(folderType);
        fileInfoQuery.setFileName(fileName);
        fileInfoQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        fileInfoQuery.setArchived(0);
        if (excludeFileId != null) {
            fileInfoQuery.setExcludeFileIdArray(new String[]{excludeFileId});
        }
        if (departmentId != null) {
            // 部门空间：同名文件属于部门共享，不应按 userId 过滤
            fileInfoQuery.setDepartmentId(departmentId);
        } else {
            fileInfoQuery.setUserId(userId);
            fileInfoQuery.setDepartmentIdIsNull(true);
        }
        Integer count = fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 0) {
            throw new BusinessException("此目录下已经存在同名" + (folderType.equals(FileFolderTypeEnum.FILE.getType()) ? "文件" : "文件夹") + "请修改名称");
        }
    }

    private String autoRename(String filePid, String userId, String fileName, String departmentId) {
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setFilePid(filePid);
        fileInfoQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        fileInfoQuery.setArchived(0);
        fileInfoQuery.setFileName(fileName);
        if (departmentId != null) {
            // 部门空间：同名文件属于部门共享，不应按 userId 过滤
            fileInfoQuery.setDepartmentId(departmentId);
        } else {
            fileInfoQuery.setUserId(userId);
            fileInfoQuery.setDepartmentIdIsNull(true);
        }
        Integer count = fileInfoMapper.selectCount(fileInfoQuery);
        if (count > 0) { fileName = StringTools.rename(fileName); }
        return fileName;
    }

    private void updateUserSpace(SessionWebUserDto sessionWebUserDto, Long fileSize) {
        UserSpaceDto userSpaceDto = redisComponent.getUserSpace(sessionWebUserDto.getUserId());
        if (userSpaceDto == null) {
            userSpaceDto = new UserSpaceDto();
            userSpaceDto.setTotalSpace(5L * Constants.MB);
            userSpaceDto.setUseSpace(0L);
        }
        userSpaceDto.setUseSpace(userSpaceDto.getUseSpace() + fileSize);
        redisComponent.saveUserSpace(sessionWebUserDto.getUserId(), userSpaceDto);
        // 同步更新用户空间（原子增量）
        try { authFeignClient.incrementUserSpace(sessionWebUserDto.getUserId(), fileSize); } catch (Exception e) { logger.warn("incrementUserSpace failed: {}", e.getMessage()); }
    }

    // ===================================================================
    // 核心算法：异步转码 — 完整保留
    // ===================================================================
    @Async
    public void transcodeFile(String fileId, SessionWebUserDto sessionWebUserDto) {
        Boolean transcodeSuccess = true;
        String targetFilePath = null;
        String cover = null;
        FileTypeEnum fileTypeEnum = null;
        FileInfo fileInfo = fileInfoMapper.selectByFileIdAndUserId(fileId, sessionWebUserDto.getUserId());
        try {
            if (fileInfo == null || !FileStatusEnum.TRANSCODING.getStatus().equals(fileInfo.getStatus())) { return; }
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = sessionWebUserDto.getUserId() + fileId;
            File fileFolder = new File(tempFolderName + "/" + currentUserFolderName);
            String fileSuffix = StringTools.getFileSuffix(fileInfo.getFileName());
            String month = DateUtils.format(fileInfo.getCreateTime(), DateTimePatternEnum.YYYYMM.getPattern());
            String targetFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
            File targetFolder = new File(targetFolderName + "/" + month);
            if (!targetFolder.exists()) { targetFolder.mkdirs(); }
            String realFileName = currentUserFolderName + fileSuffix;
            targetFilePath = targetFolder.getPath() + "/" + realFileName;

            mergeFileChunks(fileFolder.getPath(), targetFilePath, fileInfo.getFileName(), true);
            fileTypeEnum = FileTypeEnum.getFileTypeBySuffix(fileSuffix);

            if (fileTypeEnum == FileTypeEnum.VIDEO) {
                cutFile4Video(fileId, targetFilePath);
                cover = month + "/" + currentUserFolderName + Constants.IMAGE_PNG_SUFFIX;
                String coverPath = targetFolderName + "/" + cover;
                ScaleFilter.createCover4Video(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath));
            } else if (fileTypeEnum == FileTypeEnum.IMAGE) {
                cover = month + "/" + realFileName.replace(".", "_.");
                String coverPath = targetFolderName + "/" + cover;
                Boolean created = ScaleFilter.createThumbnailWidthFFmpeg(new File(targetFilePath), Constants.LENGTH_150, new File(coverPath), false);
                if (!created) { FileUtils.copyFile(new File(targetFilePath), new File(coverPath)); }
            }
        } catch (Exception e) {
            logger.error("文件转码失败，fileId:{}，userId:{}", fileId, sessionWebUserDto.getUserId(), e);
            transcodeSuccess = false;
        } finally {
            FileInfo updateFileInfo = new FileInfo();
            updateFileInfo.setFileSize(new File(targetFilePath).length());
            updateFileInfo.setFileCover(cover);
            updateFileInfo.setStatus(transcodeSuccess ? FileStatusEnum.USING.getStatus() : FileStatusEnum.TRANSCODING_FAILED.getStatus());
            fileInfoMapper.updateWithOldStatus(fileId, sessionWebUserDto.getUserId(), updateFileInfo, FileStatusEnum.TRANSCODING.getStatus());
        }
    }

    // ===================================================================
    // 核心算法：分片合并 — 完整保留
    // ===================================================================
    private void mergeFileChunks(String dirPath, String toFilePath, String fileName, Boolean delSource) {
        File dir = new File(dirPath);
        if (!dir.exists()) { throw new BusinessException("目录不存在"); }
        File[] fileList = dir.listFiles();
        File targetFile = new File(toFilePath);
        RandomAccessFile writeFile = null;
        try {
            writeFile = new RandomAccessFile(targetFile, "rw");
            byte[] buffer = new byte[1024 * 10];
            for (int index = 0; index < fileList.length; index++) {
                int len = -1;
                File chunkFile = new File(dirPath + "/" + index);
                RandomAccessFile readFile = null;
                try {
                    readFile = new RandomAccessFile(chunkFile, "r");
                    while ((len = readFile.read(buffer)) != -1) { writeFile.write(buffer, 0, len); }
                } catch (Exception e) {
                    logger.error("合并分片失败", e);
                    throw new BusinessException("合并分片失败");
                } finally {
                    if (readFile != null) { readFile.close(); }
                }
            }
        } catch (Exception e) {
            logger.error("合并文件:{}失败", fileName, e);
            throw new BusinessException("合并文件" + fileName + "失败");
        } finally {
            if (writeFile != null) { try { writeFile.close(); } catch (IOException e) { e.printStackTrace(); } }
            if (delSource && dir.exists()) { try { FileUtils.deleteDirectory(dir); } catch (IOException e) { e.printStackTrace(); } }
        }
    }

    // ===================================================================
    // 核心算法：视频切片 — 完整保留
    // ===================================================================
    private void cutFile4Video(String fileId, String videoFilePath) {
        File tsFolder = new File(videoFilePath.substring(0, videoFilePath.lastIndexOf(".")));
        if (!tsFolder.exists()) { tsFolder.mkdirs(); }
        final String CMD_TRANSFER_2_TS = "ffmpeg -y -i %s  -vcodec copy -acodec copy -bsf:v h264_mp4toannexb %s";
        final String CMD_CUT_TS = "ffmpeg -i %s -c copy -map 0 -f segment -segment_list %s -segment_time 30 %s/%s_%%4d.ts";
        String tsPath = tsFolder + "/" + Constants.TS_NAME;
        String cmd = String.format(CMD_TRANSFER_2_TS, videoFilePath, tsPath);
        ProcessUtils.executeCommand(cmd, false);
        cmd = String.format(CMD_CUT_TS, tsPath, tsFolder.getPath() + "/" + Constants.M3U8_NAME, tsFolder.getPath(), fileId);
        ProcessUtils.executeCommand(cmd, false);
        new File(tsPath).delete();
    }

    // ===================================================================
    // 权限校验辅助方法（兼容跨部门文件操作）
    // ===================================================================

    private void requireWritePermission(String fileId, String userId) {
        requirePermission(fileId, userId, "WRITE");
    }

    @SuppressWarnings("unused")
    private void requireManagePermission(String fileId, String userId) {
        requirePermission(fileId, userId, "MANAGE");
    }

    private void requirePermission(String fileId, String userId, String requiredLevel) {
        // 管理员直接放行
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null && "admin".equals(attrs.getRequest().getHeader("X-User-Role"))) return;

        FileInfo file = fileInfoMapper.selectByFileId(fileId);
        if (file == null) throw new BusinessException("文件不存在");
        if (userId.equals(file.getUserId())) return; // owner
        throw new BusinessException("无权操作此文件");
    }

    private boolean hasSufficient(String actual, String required) {
        if ("MANAGE".equals(actual)) return true;
        if ("WRITE".equals(actual) && !"MANAGE".equals(required)) return true;
        return actual.equals(required);
    }

    /** 删除权限：个人文件所有者可删，部门文件仅管理员和部门主管可删 */
    private void checkDeletePermission(FileInfo file, String userId, boolean isAdmin) {
        if (isAdmin) return;
        // 部门文件：只有部门主管可删，上传者本人也不能删
        if (file.getDepartmentId() != null) {
            if (departmentService.isDeptHead(file.getDepartmentId(), userId)) return;
            throw new BusinessException("无权删除部门文件：" + file.getFileName());
        }
        // 个人文件：所有者可删
        if (userId.equals(file.getUserId())) return;
        throw new BusinessException("无权操作文件：" + file.getFileName());
    }

    /** 移动权限：个人文件所有者可移，部门文件同部门成员可移 */
    private void checkMovePermission(FileInfo file, String userId, boolean isAdmin) {
        if (isAdmin) return;
        if (userId.equals(file.getUserId())) return;
        if (file.getDepartmentId() != null && isSameDepartment(file.getDepartmentId(), userId)) return;
        throw new BusinessException("无权移动文件：" + file.getFileName());
    }

    private boolean isSameDepartment(String fileDepartmentId, String userId) {
        if (fileDepartmentId == null || userId == null) return false;
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return false;
        String userDeptId = attrs.getRequest().getHeader("X-User-DepartmentId");
        return fileDepartmentId.equals(userDeptId);
    }

    // ===================================================================
    // 上传完成审计（仅秒传和最后一个分片触发，避免逐分片重复审计）
    // ===================================================================
    private void writeUploadAudit(SessionWebUserDto dto, String fileId, String fileName) {
        try {
            String userName = "";
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                String encodedName = attrs.getRequest().getHeader("X-User-NickName");
                if (encodedName != null) {
                    userName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
                }
            }
            FileInfo fileInfo = fileInfoMapper.selectByFileId(fileId);
            String deptId = fileInfo != null ? fileInfo.getDepartmentId() : null;
            auditLogService.log(dto.getUserId(), userName, "UPLOAD", "FILE", fileId, fileName, deptId);
        } catch (Exception e) {
            logger.warn("审计日志写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    // ===================================================================
    // 归档 / 取消归档 — 仅部门空间文件，仅 admin + 部门主管
    // ===================================================================
    @Override
    public void archiveFile(String userId, String fileIds, boolean isAdmin) {
        String[] ids = fileIds.split(",");
        List<String> subFileIds = new ArrayList<>();

        // 权限检查 + 收集子文件
        for (String fileId : ids) {
            String tid = fileId.trim();
            FileInfo file = fileInfoMapper.selectByFileId(tid);
            if (file == null) continue;
            checkArchivePermission(file, userId, isAdmin);
            if (file.getFolderType() != null && file.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
                findAllSubFolderFileList(subFileIds, tid, FileDelFlagEnum.USING.getFlag());
            }
        }

        // 标记子文件
        if (!subFileIds.isEmpty()) {
            FileInfo subUpdate = new FileInfo();
            subUpdate.setArchived(1);
            subUpdate.setArchivedTime(new Date());
            subUpdate.setLastUpdateUserId(userId);
            for (String sid : subFileIds) {
                fileInfoMapper.updateByFileId(subUpdate, sid);
                fileSearchService.sync(FileDocument.from(fileInfoMapper.selectByFileId(sid)));
            }
        }

        // 标记顶层文件/文件夹（移到根目录）
        Date now = new Date();
        for (String fileId : ids) {
            String tid = fileId.trim();
            FileInfo update = new FileInfo();
            update.setArchived(1);
            update.setArchivedTime(now);
            update.setLastUpdateUserId(userId);
            update.setFilePid(Constants.ZERO_STR);
            fileInfoMapper.updateByFileId(update, tid);
            fileSearchService.sync(FileDocument.from(fileInfoMapper.selectByFileId(tid)));
        }
    }

    @Override
    public void unarchiveFile(String userId, String fileIds, boolean isAdmin) {
        String[] ids = fileIds.split(",");
        List<String> subFileIds = new ArrayList<>();

        // 权限检查 + 收集子文件
        for (String fileId : ids) {
            String tid = fileId.trim();
            FileInfo file = fileInfoMapper.selectByFileId(tid);
            if (file == null) continue;
            checkArchivePermission(file, userId, isAdmin);
            if (file.getFolderType() != null && file.getFolderType().equals(FileFolderTypeEnum.FOLDER.getType())) {
                findAllSubFolderFileList(subFileIds, tid, FileDelFlagEnum.USING.getFlag());
            }
        }

        // 取消归档子文件（含同名冲突检测）
        if (!subFileIds.isEmpty()) {
            for (String sid : subFileIds) {
                FileInfo sub = fileInfoMapper.selectByFileId(sid);
                if (sub != null) {
                    checkUnarchiveConflict(sub);
                }
                FileInfo subUpdate = new FileInfo();
                subUpdate.setArchived(0);
                subUpdate.setArchivedTime(null);
                subUpdate.setLastUpdateUserId(userId);
                fileInfoMapper.updateByFileId(subUpdate, sid);
                fileSearchService.sync(FileDocument.from(fileInfoMapper.selectByFileId(sid)));
            }
        }

        // 取消归档顶层文件/文件夹（含同名冲突检测）
        for (String fileId : ids) {
            String tid = fileId.trim();
            FileInfo file = fileInfoMapper.selectByFileId(tid);
            if (file != null) {
                checkUnarchiveConflict(file);
            }
            FileInfo update = new FileInfo();
            update.setArchived(0);
            update.setArchivedTime(null);
            update.setLastUpdateUserId(userId);
            fileInfoMapper.updateByFileId(update, tid);
            fileSearchService.sync(FileDocument.from(fileInfoMapper.selectByFileId(tid)));
        }
    }

    private void checkArchivePermission(FileInfo file, String userId, boolean isAdmin) {
        if (isAdmin) return;
        // 仅部门空间文件可归档
        if (file.getDepartmentId() == null || file.getDepartmentId().isEmpty()) {
            throw new BusinessException("个人空间文件不支持归档");
        }
        // 仅部门主管可归档
        if (!departmentService.isDeptHead(file.getDepartmentId(), userId)) {
            throw new BusinessException("仅部门主管可归档部门文件");
        }
    }

    /** 取消归档前检测目标位置是否有同名非归档文件，有则重命名 */
    private void checkUnarchiveConflict(FileInfo file) {
        FileInfoQuery dupQuery = new FileInfoQuery();
        dupQuery.setFilePid(file.getFilePid());
        dupQuery.setFileName(file.getFileName());
        dupQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        dupQuery.setArchived(0);
        if (file.getDepartmentId() != null) {
            dupQuery.setDepartmentId(file.getDepartmentId());
        } else {
            dupQuery.setUserId(file.getUserId());
        }
        if (fileInfoMapper.selectCount(dupQuery) > 0) {
            FileInfo renameInfo = new FileInfo();
            renameInfo.setFileName(StringTools.rename(file.getFileName()));
            fileInfoMapper.updateByFileId(renameInfo, file.getFileId());
        }
    }

    // ===================================================================
    // 拷入/拷出：个人↔部门空间互拷
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void copyFile(String fileId, String targetFolderId, String userId, String targetDepartmentId) {
        boolean toDept = targetDepartmentId != null && !targetDepartmentId.isEmpty();
        FileInfo source = fileInfoMapper.selectByFileId(fileId);
        if (source == null) throw new BusinessException(ResponseCodeEnum.CODE_600);

        // 目标目录同名查重（循环重命名直到无冲突）
        FileInfoQuery dupQuery = new FileInfoQuery();
        dupQuery.setFilePid(targetFolderId);
        dupQuery.setDelFlag(FileDelFlagEnum.USING.getFlag());
        dupQuery.setArchived(0);
        if (toDept) {
            dupQuery.setDeptMode(true);
            dupQuery.setDepartmentIds(List.of(targetDepartmentId));
        } else {
            dupQuery.setUserId(userId);
            dupQuery.setDepartmentIdIsNull(true);
        }
        java.util.Set<String> existingNames = fileInfoMapper.selectList(dupQuery).stream()
                .map(FileInfo::getFileName).collect(Collectors.toSet());
        while (existingNames.contains(source.getFileName())) {
            source.setFileName(StringTools.rename(source.getFileName()));
        }

        // 递归拷贝（部门→个人时不按 userId 过滤，部门文件夹内含多人上传的文件）
        List<FileInfo> copyList = new ArrayList<>();
        Date now = new Date();
        if (toDept) {
            findAllSubFolderFileListCopy(copyList, source, source.getUserId(), userId, now, targetFolderId);
        } else {
            collectAllSubFiles(copyList, source, userId, now, targetFolderId);
        }
        for (FileInfo item : copyList) {
            item.setDepartmentId(toDept ? targetDepartmentId : null);
        }

        // 校验空间
        long totalSize = 0;
        for (FileInfo item : copyList) {
            if (FileFolderTypeEnum.FILE.getType().equals(item.getFolderType())) {
                totalSize += item.getFileSize() != null ? item.getFileSize() : 0;
            }
        }
        UserSpaceDto spaceDto = redisComponent.getUserSpace(userId);
        if (spaceDto != null && totalSize + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }

        fileInfoMapper.insertBatch(copyList);

        // 同步 ES
        for (FileInfo item : copyList) {
            fileSearchService.sync(FileDocument.from(item));
        }

        // 同步已用空间
        Long useSpace = fileInfoMapper.selectUseSpaceByUserId(userId);
        if (spaceDto != null) {
            spaceDto.setUseSpace(useSpace);
            redisComponent.saveUserSpace(userId, spaceDto);
        }
        try { authFeignClient.updateUserSpace(userId, useSpace); } catch (Exception e) {
            logger.warn("updateUserSpace failed: {}", e.getMessage());
        }
    }
}
