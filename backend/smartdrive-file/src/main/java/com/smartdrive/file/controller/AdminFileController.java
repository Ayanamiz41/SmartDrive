package com.smartdrive.file.controller;

import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.file.entity.query.FileInfoQuery;
import com.smartdrive.file.service.FileInfoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminFileController extends CommonFileController {

    private final FileInfoService fileInfoService;

    public AdminFileController(FileInfoService fileInfoService) {
        this.fileInfoService = fileInfoService;
    }

    @RequestMapping("/admin/loadFileList")
    public ResponseVO loadFileList(FileInfoQuery fileInfoQuery) {
        fileInfoQuery.setOrderBy("last_update_time desc");
        fileInfoQuery.setQueryUserNickName(true);
        return getSuccessResponseVO(fileInfoService.findListByPage(fileInfoQuery));
    }

    @RequestMapping("/admin/getFolderInfo")
    public ResponseVO getFolderInfo(@RequestParam String path) {
        return super.getFolderInfo(path, null);
    }

    @GetMapping("/admin/getFile/{userId}/{fileId}")
    public void getFile(HttpServletResponse response,
                        @PathVariable String userId, @PathVariable String fileId) {
        super.getFile(response, fileId, userId);
    }

    @GetMapping("/admin/ts/getVideoInfo/{userId}/{fileId}")
    public void getVideoInfo(HttpServletResponse response,
                             @PathVariable String userId, @PathVariable String fileId) {
        super.getFile(response, fileId, userId);
    }

    @RequestMapping("/admin/createDownloadUrl/{userId}/{fileId}")
    public ResponseVO createDownloadUrl(@PathVariable String userId, @PathVariable String fileId) {
        return super.createDownloadUrl(fileId, userId);
    }

    @GetMapping("/admin/download/{code}")
    public void download(HttpServletRequest request, HttpServletResponse response, @PathVariable String code) throws Exception {
        super.download(request, response, code);
    }

    @PostMapping("/admin/delFile")
    public ResponseVO delFile(@RequestParam String fileIdAndUserIds) {
        String[] arr = fileIdAndUserIds.split(",");
        for (String item : arr) {
            String[] parts = item.split("_");
            fileInfoService.delFileBatch(parts[0], parts[1], true);
        }
        return getSuccessResponseVO(null);
    }
}
