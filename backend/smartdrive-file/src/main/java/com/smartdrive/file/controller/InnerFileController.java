package com.smartdrive.file.controller;

import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.dto.DownloadFileDto;
import com.smartdrive.common.enums.FileDelFlagEnum;
import com.smartdrive.common.enums.FileFolderTypeEnum;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.file.component.FileRedisComponent;
import com.smartdrive.file.config.FileAppConfig;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.entity.query.FileInfoQuery;
import com.smartdrive.file.service.FileInfoService;
import com.smartdrive.file.service.DepartmentService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内部接口，供其他微服务通过 Feign 调用，不走 Gateway 鉴权
 */
@RestController
@RequestMapping("/inner")
public class InnerFileController {

    private final FileInfoService fileInfoService;
    private final FileRedisComponent redisComponent;
    private final FileAppConfig appConfig;
    private final DepartmentService departmentService;

    public InnerFileController(FileInfoService fileInfoService, FileRedisComponent redisComponent,
                                FileAppConfig appConfig, DepartmentService departmentService) {
        this.fileInfoService = fileInfoService;
        this.redisComponent = redisComponent;
        this.appConfig = appConfig;
        this.departmentService = departmentService;
    }

    /** 批量获取文件名，key=fileId, value=fileName */
    @PostMapping("/file/batch")
    public Map<String, String> getFileNames(@RequestBody List<String> fileIds,
                                            @RequestHeader("X-User-Id") String userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return new HashMap<>();
        }
        FileInfoQuery query = new FileInfoQuery();
        query.setFileIdArray(fileIds.toArray(new String[0]));
        query.setUserId(userId);
        List<FileInfo> files = fileInfoService.findListByParam(query);
        return files.stream().collect(Collectors.toMap(FileInfo::getFileId, FileInfo::getFileName, (a, b) -> a));
    }

    /** 批量获取文件基本信息（不限用户，审批用），key=fileId, value={fileName,departmentId,filePid,deleted} */
    @PostMapping("/file/batchInfo")
    public Map<String, Map<String, Object>> getFileBatchInfo(@RequestBody List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return new HashMap<>();
        }
        FileInfoQuery query = new FileInfoQuery();
        query.setFileIdArray(fileIds.toArray(new String[0]));
        List<FileInfo> files = fileInfoService.findListByParam(query);
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (FileInfo f : files) {
            Map<String, Object> info = new HashMap<>();
            info.put("fileName", f.getFileName());
            info.put("departmentId", f.getDepartmentId());
            info.put("filePid", f.getFilePid());
            // delFlag=1 表示已删除
            info.put("deleted", Integer.valueOf(1).equals(f.getDelFlag()));
            info.put("archived", Integer.valueOf(1).equals(f.getArchived()));
            result.put(f.getFileId(), info);
        }
        return result;
    }

    /** 获取单个文件信息（Feign 用，不限用户） */
    @GetMapping("/file/{fileId}")
    public Map<String, Object> getFileInfo(@PathVariable String fileId,
                                           @RequestHeader(value = "X-User-Id", required = false) String userId) {
        FileInfo fileInfo = fileInfoService.getFileInfoByFileId(fileId);
        if (fileInfo == null) return new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        result.put("fileId", fileInfo.getFileId());
        result.put("fileName", fileInfo.getFileName());
        result.put("filePath", fileInfo.getFilePath());
        result.put("fileSize", fileInfo.getFileSize());
        result.put("fileCover", fileInfo.getFileCover());
        result.put("userId", fileInfo.getUserId());
        result.put("departmentId", fileInfo.getDepartmentId());
        result.put("folderType", fileInfo.getFolderType());
        result.put("fileType", fileInfo.getFileType());
        result.put("fileCategory", fileInfo.getFileCategory());
        result.put("status", fileInfo.getStatus());
        result.put("lastUpdateTime", fileInfo.getLastUpdateTime());
        result.put("summary", fileInfo.getSummary() != null ? fileInfo.getSummary() : "");
        return result;
    }

    /** 获取子文件列表（分享用，不限用户） */
    @PostMapping("/file/listByPid")
    public List<Map<String, Object>> listFilesByPid(@RequestParam String filePid,
                                                     @RequestParam(required = false) String category) {
        FileInfoQuery query = new FileInfoQuery();
        query.setFilePid(filePid);
        query.setDelFlag(FileDelFlagEnum.USING.getFlag());
        if (category != null && !category.isEmpty() && !"all".equals(category)) {
            query.setFileCategory(Integer.parseInt(category));
        }
        List<FileInfo> files = fileInfoService.findListByParam(query);
        return files.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fileId", f.getFileId());
            m.put("fileName", f.getFileName());
            m.put("fileSize", f.getFileSize());
            m.put("fileCover", f.getFileCover());
            m.put("fileType", f.getFileType());
            m.put("folderType", f.getFolderType());
            m.put("lastUpdateTime", f.getLastUpdateTime());
            m.put("status", f.getStatus());
            return m;
        }).collect(Collectors.toList());
    }

    /** 生成下载码，存 Redis，返回码（分享下载用） */
    @PostMapping("/file/createDownloadCode/{fileId}")
    public Map<String, String> createDownloadCode(@PathVariable String fileId) {
        FileInfo fileInfo = fileInfoService.getFileInfoByFileId(fileId);
        if (fileInfo == null || FileFolderTypeEnum.FOLDER.getType().equals(fileInfo.getFolderType())) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        String code = StringTools.getRandomString(Constants.LENGTH_50);
        DownloadFileDto dto = new DownloadFileDto();
        dto.setFileName(fileInfo.getFileName());
        dto.setFilePath(fileInfo.getFilePath());
        dto.setDownloadCode(code);
        redisComponent.saveDownloadCode(dto);
        return Map.of("code", code);
    }

    /** 根据下载码获取文件二进制（分享下载用） */
    @GetMapping("/file/downloadByCode/{code}")
    public ResponseEntity<Resource> downloadByCode(@PathVariable String code) throws IOException {
        DownloadFileDto dto = redisComponent.getDownloadDto(code);
        if (dto == null) return ResponseEntity.notFound().build();
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + "/" + dto.getFilePath();
        File file = new File(filePath);
        if (!file.exists()) return ResponseEntity.notFound().build();
        byte[] bytes = Files.readAllBytes(file.toPath());
        ByteArrayResource resource = new ByteArrayResource(bytes);
        String fileName = URLEncoder.encode(dto.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename*=UTF-8''" + fileName)
                .body(resource);
    }

    /** 获取文件二进制内容（分享预览用） */
    @GetMapping("/file/content/{fileId}")
    public ResponseEntity<Resource> getFileContent(@PathVariable String fileId) throws IOException {
        FileInfo fileInfo = fileInfoService.getFileInfoByFileId(fileId);
        if (fileInfo == null) return ResponseEntity.notFound().build();
        String filePath = getPhysicalPath(fileInfo);
        File file = new File(filePath);
        if (!file.exists()) return ResponseEntity.notFound().build();
        byte[] bytes = Files.readAllBytes(file.toPath());
        ByteArrayResource resource = new ByteArrayResource(bytes);
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if (fileInfo.getFileCategory() != null && fileInfo.getFileCategory() == 3) {
            contentType = "video/mp4";
        } else if (fileInfo.getFileCategory() != null && fileInfo.getFileCategory() == 1) {
            contentType = "image/jpeg";
        }
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType)).body(resource);
    }

    /** 转存分享文件 */
    @PostMapping("/file/saveShare")
    public Map<String, Object> saveShare(@RequestParam String shareRootFileId,
                                          @RequestParam String shareFileIds,
                                          @RequestParam String myFolderId,
                                          @RequestParam String shareUserId,
                                          @RequestParam(required = false) String targetDepartmentId,
                                          @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        fileInfoService.saveShare(shareRootFileId, shareFileIds, myFolderId, shareUserId, targetDepartmentId, currentUserId);
        return Map.of("ok", true);
    }

    @GetMapping("/file/isDeptHead")
    public boolean isDeptHead(@RequestParam String departmentId,
                               @RequestHeader("X-User-Id") String userId) {
        return departmentService.isDeptHead(departmentId, userId);
    }

    /** auth服务内部调用：获取用户已用空间 */
    @GetMapping("/file/userSpace/{userId}")
    public Long getUserUsedSpace(@PathVariable String userId) {
        return fileInfoService.getUsedSpace(userId);
    }

    /** 获取文件夹路径（分享导航用） */
    @PostMapping("/file/folderPath")
    public List<Map<String, Object>> getFolderPath(@RequestParam String path, @RequestParam String userId) {
        FileInfoQuery query = new FileInfoQuery();
        query.setUserId(userId);
        query.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        query.setFileIdArray(path.split("/"));
        query.setOrderBy("field(f.file_id,'" + String.join("','", path.split("/")) + "')");
        List<FileInfo> list = fileInfoService.findListByParam(query);
        return list.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fileId", f.getFileId());
            m.put("fileName", f.getFileName());
            return m;
        }).collect(Collectors.toList());
    }

    private String getPhysicalPath(FileInfo fileInfo) {
        String basePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
        String fileId = fileInfo.getFileId();
        String filePath = fileInfo.getFilePath();
        if (fileId.endsWith(".ts")) {
            String realFileId = fileId.split("_")[0];
            String fileName = StringTools.getFileNameNoSuffix(filePath) + "/" + fileId;
            return basePath + "/" + fileName;
        }
        // 视频用 M3U8
        if (fileInfo.getFileCategory() != null && fileInfo.getFileCategory() == 3) {
            String noSuffix = StringTools.getFileNameNoSuffix(filePath);
            return basePath + "/" + noSuffix + "/" + Constants.M3U8_NAME;
        }
        return basePath + "/" + filePath;
    }
}
