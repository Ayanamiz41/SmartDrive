package com.smartdrive.file.task;

import com.smartdrive.common.enums.FileDelFlagEnum;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.entity.query.FileInfoQuery;
import com.smartdrive.file.service.FileInfoService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FileCleanTask {

    private final FileInfoService fileInfoService;

    public FileCleanTask(FileInfoService fileInfoService) {
        this.fileInfoService = fileInfoService;
    }

    @Scheduled(fixedDelay = 1000 * 60 * 3)
    public void execute() {
        // 清理回收站过期文件
        FileInfoQuery query = new FileInfoQuery();
        query.setDelFlag(FileDelFlagEnum.RECYCLE.getFlag());
        query.setQueryExpire(true);
        List<FileInfo> fileInfoList = fileInfoService.findListByParam(query);
        Map<String, List<FileInfo>> fileInfoMap = fileInfoList.stream()
                .collect(Collectors.groupingBy(FileInfo::getUserId));
        for (Map.Entry<String, List<FileInfo>> entry : fileInfoMap.entrySet()) {
            List<String> fileIds = entry.getValue().stream().map(FileInfo::getFileId).collect(Collectors.toList());
            fileInfoService.delFileBatch(entry.getKey(), String.join(",", fileIds), false);
        }
    }
}
