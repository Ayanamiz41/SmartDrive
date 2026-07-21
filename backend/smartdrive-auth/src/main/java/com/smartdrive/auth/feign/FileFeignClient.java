package com.smartdrive.auth.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@FeignClient(name = "smartdrive-file")
public interface FileFeignClient {

    @GetMapping("/api/inner/file/userSpace/{userId}")
    Long getUserUsedSpace(@PathVariable String userId);

    /** 批量获取文件基本信息（fileName, departmentId, filePid, deleted） */
    @PostMapping("/api/inner/file/batchInfo")
    Map<String, Map<String, Object>> getFileBatchInfo(@RequestBody List<String> fileIds);
}
