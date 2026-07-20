package com.smartdrive.file.service;

import com.smartdrive.file.elasticsearch.FileDocument;
import com.smartdrive.file.elasticsearch.FileSearchRepository;
import com.smartdrive.file.elasticsearch.FileSearchRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class FileSearchService {

    private static final Logger log = LoggerFactory.getLogger(FileSearchService.class);

    private final ElasticsearchOperations operations;
    private final FileSearchRepository repository;

    public FileSearchService(ElasticsearchOperations operations,
                             FileSearchRepository repository) {
        this.operations = operations;
        this.repository = repository;
    }

    /**
     * 全文搜索，返回 fileId 列表（按相关性分数降序）。
     * 所有筛选维度收敛在 FileSearchRequest：扩展新维度 = Request 加字段 + 此处加一个 if-Criteria。
     */
    public org.springframework.data.elasticsearch.core.SearchHits<FileDocument> search(FileSearchRequest req) {
        boolean hasKeyword = req.getKeyword() != null && !req.getKeyword().isEmpty();
        // 起链必须用真实字段（Criteria 空构造是坑），delFlag 恒有
        Criteria criteria = new Criteria("delFlag").is(2);
        if (hasKeyword) {
            criteria = criteria.and(new Criteria("fileName").matches(req.getKeyword()));
        }

        // 权限域：部门空间 or 个人空间
        if (req.getDepartmentId() != null && !req.getDepartmentId().isEmpty()) {
            criteria = criteria.and(new Criteria("departmentId").is(req.getDepartmentId()));
        } else {
            criteria = criteria.and(new Criteria("userId").is(req.getUserId()))
                .and(new Criteria("departmentId").is("__none__"));
        }

        // ===== 可选筛选维度（新维度在此追加） =====
        if (req.getFileCategory() != null) {
            criteria = criteria.and(new Criteria("fileCategory").is(req.getFileCategory()));
        }
        if (req.getUploaderUserIds() != null && !req.getUploaderUserIds().isEmpty()) {
            criteria = criteria.and(new Criteria("userId").in(req.getUploaderUserIds()));
        }
        if (req.getCreateTimeStart() != null) {
            criteria = criteria.and(new Criteria("createTime").greaterThanEqual(req.getCreateTimeStart()));
        }
        if (req.getCreateTimeEnd() != null) {
            criteria = criteria.and(new Criteria("createTime").lessThanEqual(req.getCreateTimeEnd()));
        }
        if (req.getArchived() != null) {
            criteria = criteria.and(new Criteria("archived").is(req.getArchived()));
        }
        if (req.getArchivedTimeStart() != null) {
            criteria = criteria.and(new Criteria("archivedTime").greaterThanEqual(req.getArchivedTimeStart()));
        }
        if (req.getArchivedTimeEnd() != null) {
            criteria = criteria.and(new Criteria("archivedTime").lessThanEqual(req.getArchivedTimeEnd()));
        }

        CriteriaQuery query = new CriteriaQuery(criteria)
            .setPageable(PageRequest.of(req.getOffset() / req.getPageSize(), req.getPageSize()));
        if (req.getSortField() != null && req.getSortOrder() != null) {
            log.info("ES search sort: field={}, order={}", req.getSortField(), req.getSortOrder());
            org.springframework.data.domain.Sort.Direction dir =
                "ascending".equals(req.getSortOrder())
                    ? org.springframework.data.domain.Sort.Direction.ASC
                    : org.springframework.data.domain.Sort.Direction.DESC;
            query.addSort(org.springframework.data.domain.Sort.by(dir, req.getSortField()));
        } else if (!hasKeyword) {
            // 纯筛选模式无相关性分数，按创建时间倒序
            query.addSort(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createTime"));
        }

        // debug: 打印查询条件
        log.info("ES search: keyword={}, departmentId={}, userId={}, archived={}, criteria={}",
                req.getKeyword(), req.getDepartmentId(), req.getUserId(), req.getArchived(), criteria);

        SearchHits<FileDocument> hits = operations.search(query, FileDocument.class);
        log.info("ES search result: totalHits={}, keyword={}", hits.getTotalHits(), req.getKeyword());
        return hits;
    }

    /**
     * 异步同步单个文件到 ES
     */
    @Async
    public void sync(FileDocument doc) {
        try {
            if (doc.getDelFlag() != null && doc.getDelFlag() != 2) {
                repository.deleteById(doc.getFileId());
            } else {
                repository.save(doc);
            }
        } catch (Exception e) {
            log.warn("ES sync failed for fileId={}: {}", doc.getFileId(), e.getMessage(), e);
        }
    }

    /**
     * 异步从 ES 删除文档
     */
    @Async
    public void delete(String fileId) {
        try {
            repository.deleteById(fileId);
        } catch (Exception e) {
            log.warn("ES delete failed for fileId={}: {}", fileId, e.getMessage());
        }
    }

    /**
     * 批量索引（初始化用）
     */
    public void bulkIndex(List<FileDocument> docs) {
        repository.saveAll(docs);
    }
}
