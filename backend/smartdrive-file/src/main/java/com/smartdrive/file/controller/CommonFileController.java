package com.smartdrive.file.controller;

import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.dto.DownloadFileDto;
import com.smartdrive.common.enums.*;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.utils.CopyTools;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.common.vo.FolderVO;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.file.component.FileRedisComponent;
import com.smartdrive.file.config.FileAppConfig;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.entity.query.FileInfoQuery;
import com.smartdrive.file.service.FileInfoService;
import com.smartdrive.common.controller.BaseController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.net.URLEncoder;
import java.util.List;

public class CommonFileController extends BaseController {

    @Autowired
    protected FileAppConfig appConfig;

    @Autowired
    protected FileInfoService fileInfoService;

    @Autowired
    protected FileRedisComponent redisComponent;

    protected void getImage(HttpServletResponse response, String imageFolder, String imageName) {
        if (StringTools.isEmpty(imageFolder) || StringTools.isEmpty(imageName) || !StringTools.pathIsOk(imageFolder)) { return; }
        String imageSuffix = StringTools.getFileSuffix(imageName).replace(".", "");
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + "/" + imageFolder + "/" + imageName;
        response.setContentType("image/" + imageSuffix);
        response.setHeader("Cache-Control", "max-age=2592000");
        readFile(response, filePath);
    }

    protected void getFile(HttpServletResponse response, String fileId, String userId) {
        String filePath = null;
        if (fileId.endsWith(".ts")) {
            String[] tsArray = fileId.split("_");
            String realFileId = tsArray[0];
            FileInfo fileInfo = fileInfoService.getFileInfoByFileId(realFileId);
            if (fileInfo == null) { return; }
            String fileName = StringTools.getFileNameNoSuffix(fileInfo.getFilePath()) + "/" + fileId;
            filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + "/" + fileName;
        } else {
            FileInfo fileInfo = fileInfoService.getFileInfoByFileId(fileId);
            if (fileInfo == null) { return; }
            if (FileCatogoryEnum.VIDEO.getCategory().equals(fileInfo.getFileCategory())) {
                String fileNameNoSuffix = StringTools.getFileNameNoSuffix(fileInfo.getFilePath());
                filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + "/" + fileNameNoSuffix + "/" + Constants.M3U8_NAME;
            } else {
                filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + "/" + fileInfo.getFilePath();
            }
        }
        File file = new File(filePath);
        if (!file.exists()) { return; }
        readFile(response, filePath);
    }

    protected ResponseVO getFolderInfo(String path, String userId) {
        String[] pathArray = path.split("/");
        FileInfoQuery fileInfoQuery = new FileInfoQuery();
        fileInfoQuery.setUserId(userId);
        fileInfoQuery.setFolderType(FileFolderTypeEnum.FOLDER.getType());
        fileInfoQuery.setFileIdArray(pathArray);
        String orderBy = "field(f.file_id,\"" + StringUtils.join(pathArray, "\",\"") + "\")";
        fileInfoQuery.setOrderBy(orderBy);
        List<FileInfo> fileInfoList = fileInfoService.findListByParam(fileInfoQuery);
        return getSuccessResponseVO(CopyTools.copyList(fileInfoList, FolderVO.class));
    }

    protected ResponseVO createDownloadUrl(String fileId, String userId) {
        FileInfo fileInfo = fileInfoService.getFileInfoByFileId(fileId);
        if (fileInfo == null) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        if (FileFolderTypeEnum.FOLDER.getType().equals(fileInfo.getFolderType())) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        String code = StringTools.getRandomString(Constants.LENGTH_50);
        DownloadFileDto downloadFileDto = new DownloadFileDto();
        downloadFileDto.setFileName(fileInfo.getFileName());
        downloadFileDto.setFilePath(fileInfo.getFilePath());
        downloadFileDto.setDownloadCode(code);
        redisComponent.saveDownloadCode(downloadFileDto);
        return getSuccessResponseVO(code);
    }

    protected void download(HttpServletRequest request, HttpServletResponse response, String code) throws Exception {
        DownloadFileDto downloadFileDto = redisComponent.getDownloadDto(code);
        if (downloadFileDto == null) { return; }
        String filePath = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE + "/" + downloadFileDto.getFilePath();
        String fileName = downloadFileDto.getFileName();
        response.setContentType("application/x-msdownload;charset=utf-8");
        if (request.getHeader("User-Agent").toLowerCase().indexOf("msie") > 0) {
            fileName = URLEncoder.encode(fileName, "utf-8");
        } else {
            fileName = new String(fileName.getBytes("UTF-8"), "ISO8859-1");
        }
        response.setHeader("Content-Disposition", "attachment;filename=\"" + fileName + "\"");
        readFile(response, filePath);
    }
}
