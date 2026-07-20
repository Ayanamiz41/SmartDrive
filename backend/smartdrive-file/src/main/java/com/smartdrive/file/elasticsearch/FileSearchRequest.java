package com.smartdrive.file.elasticsearch;

/**
 * 搜索条件对象 — 所有可选筛选维度集中于此。
 * 扩展新维度三步：此处加字段 → FileSearchService.search() 加一个 if-Criteria → SearchController 透传参数。
 */
public class FileSearchRequest {

    /** 关键字（fileName 全文检索，可空 — 空时为纯条件筛选模式，按创建时间倒序） */
    private String keyword;
    /** 权限域：个人模式的当前用户 */
    private String userId;
    /** 权限域：部门模式的部门ID（非空时按部门过滤，忽略 userId） */
    private String departmentId;
    /** 筛选：文件分类（FileCatogoryEnum.category，可空） */
    private Integer fileCategory;
    /** 筛选：上传者 userId 集合（可空；由 controller 将昵称/精确ID解析合并而来） */
    private java.util.List<String> uploaderUserIds;
    /** 筛选：创建时间范围起（epoch millis，可空） */
    private Long createTimeStart;
    /** 筛选：创建时间范围止（epoch millis，可空） */
    private Long createTimeEnd;
    /** 归档模式：0=仅正常 1=仅归档 null=不限（兼容旧调用） */
    private Integer archived;
    /** 筛选：归档时间范围起（epoch millis，可空） */
    private Long archivedTimeStart;
    /** 筛选：归档时间范围止（epoch millis，可空） */
    private Long archivedTimeEnd;
    /** 排序字段 */
    private String sortField;
    /** 排序方向：ascending | descending */
    private String sortOrder;

    private int pageSize = 15;
    private int offset = 0;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public Integer getFileCategory() { return fileCategory; }
    public void setFileCategory(Integer fileCategory) { this.fileCategory = fileCategory; }
    public java.util.List<String> getUploaderUserIds() { return uploaderUserIds; }
    public void setUploaderUserIds(java.util.List<String> uploaderUserIds) { this.uploaderUserIds = uploaderUserIds; }
    public Long getCreateTimeStart() { return createTimeStart; }
    public void setCreateTimeStart(Long createTimeStart) { this.createTimeStart = createTimeStart; }
    public Long getCreateTimeEnd() { return createTimeEnd; }
    public void setCreateTimeEnd(Long createTimeEnd) { this.createTimeEnd = createTimeEnd; }
    public Integer getArchived() { return archived; }
    public void setArchived(Integer archived) { this.archived = archived; }
    public Long getArchivedTimeStart() { return archivedTimeStart; }
    public void setArchivedTimeStart(Long archivedTimeStart) { this.archivedTimeStart = archivedTimeStart; }
    public Long getArchivedTimeEnd() { return archivedTimeEnd; }
    public void setArchivedTimeEnd(Long archivedTimeEnd) { this.archivedTimeEnd = archivedTimeEnd; }
    public String getSortField() { return sortField; }
    public void setSortField(String sortField) { this.sortField = sortField; }
    public String getSortOrder() { return sortOrder; }
    public void setSortOrder(String sortOrder) { this.sortOrder = sortOrder; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }
}
