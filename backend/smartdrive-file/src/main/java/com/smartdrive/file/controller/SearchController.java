package com.smartdrive.file.controller;

import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.enums.FileCatogoryEnum;
import com.smartdrive.common.enums.DateTimePatternEnum;
import com.smartdrive.common.utils.DateUtils;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.elasticsearch.FileSearchRequest;
import com.smartdrive.file.feign.AuthFeignClient;
import com.smartdrive.file.mapper.FileInfoMapper;
import com.smartdrive.file.service.DepartmentService;
import com.smartdrive.file.service.FileSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/file")
public class SearchController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final FileSearchService searchService;
    private final FileInfoMapper fileInfoMapper;
    private final DepartmentService departmentService;
    private final AuthFeignClient authFeignClient;

    public SearchController(FileSearchService searchService,
                            FileInfoMapper fileInfoMapper,
                            DepartmentService departmentService,
                            AuthFeignClient authFeignClient) {
        this.searchService = searchService;
        this.fileInfoMapper = fileInfoMapper;
        this.departmentService = departmentService;
        this.authFeignClient = authFeignClient;
    }

    @RequestMapping("/search")
    public ResponseVO search(@RequestParam(required = false) String keyword,
                             @RequestParam(defaultValue = "false") Boolean deptMode,
                             @RequestParam(required = false) String departmentId,
                             @RequestParam(required = false) String category,
                             @RequestParam(required = false) String uploaderNickName,
                             @RequestParam(required = false) String uploaderUserId,
                             @RequestParam(required = false) Long createTimeStart,
                             @RequestParam(required = false) Long createTimeEnd,
                             @RequestParam(required = false) Integer archived,
                             @RequestParam(required = false) Long archivedTimeStart,
                             @RequestParam(required = false) Long archivedTimeEnd,
                             @RequestParam(required = false) String sortField,
                             @RequestParam(required = false) String sortOrder,
                             @RequestParam(defaultValue = "1") Integer pageNo,
                             @RequestParam(defaultValue = "15") Integer pageSize) {

        String userId = getCurrentUserId();
        String deptId = null;

        if (departmentId != null && !departmentId.isEmpty()) {
            deptId = departmentId;
        } else if (deptMode) {
            deptId = getCurrentUserDepartmentId();
        }

        // 上传者：精确 userId + 昵称模糊（user_info 同库直查）合并为 id 集合
        List<String> uploaderIds = new ArrayList<>();
        if (uploaderUserId != null && !uploaderUserId.isEmpty()) {
            uploaderIds.add(uploaderUserId);
        }
        if (uploaderNickName != null && !uploaderNickName.isEmpty()) {
            List<String> byNick = fileInfoMapper.selectUserIdsByNickName(uploaderNickName);
            if (byNick.isEmpty()) {
                // 查无此上传者 → 结果必为空，无需查 ES
                return getSuccessResponseVO(new PaginationResultVO<>(0, pageSize, pageNo, 0, Collections.emptyList()));
            }
            uploaderIds.addAll(byNick);
        }

        // 装配搜索条件对象：新筛选维度在 FileSearchRequest 加字段后此处透传即可
        FileSearchRequest req = new FileSearchRequest();
        req.setKeyword(keyword);
        req.setUserId(userId);
        req.setDepartmentId(deptId);
        FileCatogoryEnum categoryEnum = FileCatogoryEnum.getByCode(category);
        if (categoryEnum != null) { req.setFileCategory(categoryEnum.getCategory()); }
        if (!uploaderIds.isEmpty()) { req.setUploaderUserIds(uploaderIds); }
        req.setCreateTimeStart(createTimeStart);
        req.setCreateTimeEnd(createTimeEnd);
        req.setArchived(archived != null ? archived : 0);
        req.setArchivedTimeStart(archivedTimeStart);
        req.setArchivedTimeEnd(archivedTimeEnd);
        req.setSortField(sortField);
        req.setSortOrder(sortOrder);
        req.setPageSize(pageSize);
        req.setOffset((pageNo - 1) * pageSize);

        org.springframework.data.elasticsearch.core.SearchHits<com.smartdrive.file.elasticsearch.FileDocument> hits;
        long esTotal = 0;
        try {
            hits = searchService.search(req);
        } catch (Exception e) {
            log.warn("ES search failed, keyword={}: {}", keyword, e.getMessage());
            return fallbackMysqlSearch(keyword, deptMode, userId, req.getFileCategory(), req.getArchived(), pageNo, pageSize);
        }

        List<String> fileIds = new ArrayList<>();
        for (org.springframework.data.elasticsearch.core.SearchHit<com.smartdrive.file.elasticsearch.FileDocument> hit : hits) {
            fileIds.add(hit.getContent().getFileId());
        }
        esTotal = hits.getTotalHits();

        if (fileIds.isEmpty()) {
            PaginationResultVO<Map<String, Object>> empty =
                new PaginationResultVO<>((int) esTotal, pageSize, pageNo, 0, Collections.emptyList());
            return getSuccessResponseVO(empty);
        }

        // MySQL 查文件详情（不 JOIN user_info）
        List<FileInfo> files = fileInfoMapper.selectBasicByIds(fileIds);

        // Feign 批量拿昵称（上传者 + 最后编辑者）
        Map<String, String> nickNameMap = Collections.emptyMap();
        try {
            Set<String> allUserIds = new HashSet<>();
            for (FileInfo f : files) {
                if (f.getUserId() != null) allUserIds.add(f.getUserId());
                if (f.getLastUpdateUserId() != null) allUserIds.add(f.getLastUpdateUserId());
            }
            if (!allUserIds.isEmpty()) {
                Map<String, Map<String, Object>> userInfoMap =
                    authFeignClient.batchGetUserInfo(new ArrayList<>(allUserIds));
                nickNameMap = new HashMap<>();
                for (Map.Entry<String, Map<String, Object>> e : userInfoMap.entrySet()) {
                    Object nickName = e.getValue().get("nickName");
                    if (nickName != null) nickNameMap.put(e.getKey(), nickName.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Feign batchGetUserInfo failed: {}", e.getMessage());
        }

        // 组装结果，保持 ES 顺序
        Map<String, FileInfo> fileMap = new LinkedHashMap<>();
        for (FileInfo f : files) {
            fileMap.put(f.getFileId(), f);
        }

        // 批量解析所有结果的父路径：迭代批量查询，轮数 = 最大目录深度（不做逐条 N+1）
        Map<String, FileInfo> folderMap = new HashMap<>();
        Set<String> pending = new HashSet<>();
        for (FileInfo f : files) {
            if (f.getFilePid() != null && !"0".equals(f.getFilePid())) {
                pending.add(f.getFilePid());
            }
        }
        while (!pending.isEmpty()) {
            List<FileInfo> parents = fileInfoMapper.selectBasicByIds(new ArrayList<>(pending));
            pending = new HashSet<>();
            for (FileInfo p : parents) {
                folderMap.put(p.getFileId(), p);
                String gp = p.getFilePid();
                if (gp != null && !"0".equals(gp) && !folderMap.containsKey(gp)) {
                    pending.add(gp);
                }
            }
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (String id : fileIds) {
            FileInfo f = fileMap.get(id);
            if (f == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileId", f.getFileId());
            item.put("fileName", f.getFileName());
            item.put("fileSize", f.getFileSize());
            item.put("fileCover", f.getFileCover());
            item.put("fileType", f.getFileType());
            item.put("folderType", f.getFolderType());
            item.put("filePid", f.getFilePid());
            item.put("createTime", DateUtils.format(f.getCreateTime(), DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern()));
            item.put("lastUpdateTime", DateUtils.format(f.getLastUpdateTime(), DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern()));
            item.put("archivedTime", f.getArchivedTime() != null
                ? DateUtils.format(f.getArchivedTime(), DateTimePatternEnum.YYYY_MM_DD_HH_MM_SS.getPattern()) : null);
            item.put("userId", f.getUserId());
            item.put("nickName", nickNameMap.getOrDefault(f.getUserId(), f.getUserId()));
            item.put("lastUpdateUserId", f.getLastUpdateUserId());
            item.put("lastUpdateUserNickName", nickNameMap.getOrDefault(f.getLastUpdateUserId(), 
                f.getLastUpdateUserId() != null ? f.getLastUpdateUserId() : ""));
            item.put("departmentId", f.getDepartmentId());
            item.put("departmentName", f.getDepartmentName());
            item.put("status", f.getStatus());
            // 所在位置：根到父的文件夹链（根目录文件为空数组，前端统一渲染"全部文件"前缀）
            List<Map<String, String>> pathList = new ArrayList<>();
            String pid = f.getFilePid();
            int guard = 0;
            while (pid != null && !"0".equals(pid) && guard++ < 20) {
                FileInfo folder = folderMap.get(pid);
                if (folder == null) break;
                pathList.add(0, Map.of("fileId", folder.getFileId(), "fileName", folder.getFileName()));
                pid = folder.getFilePid();
            }
            item.put("pathList", pathList);
            resultList.add(item);
        }

        int total = resultList.size();
        PaginationResultVO<Map<String, Object>> result =
            new PaginationResultVO<>((int) esTotal, pageSize, pageNo,
                (int) ((esTotal + pageSize - 1) / pageSize), resultList);
        return getSuccessResponseVO(result);
    }

    /**
     * ES 不可用时回退 MySQL LIKE 查询
     */
    private ResponseVO fallbackMysqlSearch(String keyword, Boolean deptMode,
                                            String userId, Integer fileCategory,
                                            Integer archived,
                                            int pageNo, int pageSize) {
        // 复用现有 loadDataList 的 MySQL 逻辑，但这里简化：直接调 mapper
        var query = new com.smartdrive.file.entity.query.FileInfoQuery();
        query.setFileNameFuzzy(keyword);
        query.setUserId(userId);
        query.setDelFlag(2);
        query.setArchived(archived);
        query.setDeptMode(deptMode);
        query.setFileCategory(fileCategory);
        if (deptMode) {
            String deptId = getCurrentUserDepartmentId();
            if (deptId != null) query.setDepartmentIds(List.of(deptId));
        }
        int total = fileInfoMapper.selectCount(query);
        List<FileInfo> list = fileInfoMapper.selectListPage(query, (pageNo - 1) * pageSize, pageSize);
        // 通过 Feign 补充昵称
        try {
            Set<String> userIds = new HashSet<>();
            for (FileInfo f : list) {
                if (f.getUserId() != null) userIds.add(f.getUserId());
            }
            if (!userIds.isEmpty()) {
                Map<String, Map<String, Object>> userMap = authFeignClient.batchGetUserInfo(new ArrayList<>(userIds));
                for (FileInfo f : list) {
                    Map<String, Object> u = userMap.get(f.getUserId());
                    if (u != null) f.setNickName((String) u.get("nickName"));
                }
            }
        } catch (Exception e) {
            log.warn("Feign batchGetUserInfo failed in fallback: {}", e.getMessage());
        }
        PaginationResultVO<FileInfo> result =
            new PaginationResultVO<>(total, pageSize, pageNo,
                (total + pageSize - 1) / pageSize, list);
        return getSuccessResponseVO(result);
    }

    /**
     * 全量重建 ES 索引（仅管理员）。FileDocument 结构变更后必须调用一次，否则存量文档缺新字段导致筛选漏结果。
     */
    @PostMapping("/search/rebuildIndex")
    public ResponseVO rebuildIndex() {
        if (!isAdmin()) {
            throw new com.smartdrive.common.exception.BusinessException("仅管理员可重建索引");
        }
        var query = new com.smartdrive.file.entity.query.FileInfoQuery();
        query.setDelFlag(2);
        List<FileInfo> all = fileInfoMapper.selectList(query);
        List<com.smartdrive.file.elasticsearch.FileDocument> docs = all.stream()
                .map(com.smartdrive.file.elasticsearch.FileDocument::from)
                .collect(Collectors.toList());
        searchService.bulkIndex(docs);
        log.info("ES 索引重建完成，共 {} 条", docs.size());
        return getSuccessResponseVO(docs.size());
    }
}
