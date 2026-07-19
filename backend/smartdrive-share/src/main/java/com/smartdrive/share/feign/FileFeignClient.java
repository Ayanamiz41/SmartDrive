package com.smartdrive.share.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(name = "smartdrive-file")
public interface FileFeignClient {

    @GetMapping("/api/inner/file/{fileId}")
    Map<String, Object> getFileInfo(@PathVariable String fileId, @RequestHeader("X-User-Id") String userId);

    @PostMapping("/api/inner/file/batch")
    Map<String, String> getFileNames(@RequestBody java.util.List<String> fileIds, @RequestHeader("X-User-Id") String userId);

    @PostMapping("/api/inner/file/saveShare")
    Map<String, Object> saveShare(@RequestParam String shareRootFileId,
                                   @RequestParam String shareFileIds,
                                   @RequestParam String myFolderId,
                                   @RequestParam String shareUserId,
                                   @RequestParam(required = false) String targetDepartmentId,
                                   @RequestHeader("X-User-Id") String currentUserId);

    @PostMapping("/api/inner/file/listByPid")
    java.util.List<Map<String, Object>> listFilesByPid(@RequestParam String filePid,
                                                        @RequestParam(required = false) String category);

    @PostMapping("/api/inner/file/createDownloadCode/{fileId}")
    Map<String, String> createDownloadCode(@PathVariable String fileId);

    @GetMapping("/api/inner/file/downloadByCode/{code}")
    org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> downloadByCode(@PathVariable String code);

    @GetMapping("/api/inner/file/content/{fileId}")
    org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> getFileContent(@PathVariable String fileId);

    @PostMapping("/api/inner/file/folderPath")
    java.util.List<Map<String, Object>> getFolderPath(@RequestParam String path, @RequestParam String userId);

    @GetMapping("/api/inner/file/isDeptHead")
    boolean isDeptHead(@RequestParam String departmentId, @RequestHeader("X-User-Id") String userId);
}
