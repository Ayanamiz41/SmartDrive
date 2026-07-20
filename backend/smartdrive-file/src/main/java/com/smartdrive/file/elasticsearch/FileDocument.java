package com.smartdrive.file.elasticsearch;

import com.smartdrive.file.entity.FileInfo;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "smartdrive_files")
public class FileDocument {

    @Id
    private String fileId;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_max_word")
    private String fileName;

    @Field(type = FieldType.Keyword)
    private String userId;

    @Field(type = FieldType.Keyword)
    private String departmentId;

    @Field(type = FieldType.Integer)
    private Integer delFlag;

    @Field(type = FieldType.Integer)
    private Integer folderType;

    @Field(type = FieldType.Integer)
    private Integer archived;

    /** 文件分类（FileCatogoryEnum.category），筛选用 */
    @Field(type = FieldType.Integer)
    private Integer fileCategory;

    /** 创建时间（epoch millis），范围筛选用 */
    @Field(type = FieldType.Long)
    private Long createTime;

    /** 最后修改时间（epoch millis），排序用 */
    @Field(type = FieldType.Long)
    private Long lastUpdateTime;

    /** 文件大小（字节），排序用 */
    @Field(type = FieldType.Long)
    private Long fileSize;

    /** 归档时间（epoch millis） */
    @Field(type = FieldType.Long)
    private Long archivedTime;

    public static FileDocument from(FileInfo file) {
        FileDocument doc = new FileDocument();
        doc.fileId = file.getFileId();
        doc.fileName = file.getFileName();
        doc.userId = file.getUserId();
        doc.departmentId = file.getDepartmentId() != null ? file.getDepartmentId() : "__none__";
        doc.delFlag = file.getDelFlag();
        doc.archived = file.getArchived() != null ? file.getArchived() : 0;
        doc.archivedTime = file.getArchivedTime() != null ? file.getArchivedTime().getTime() : null;
        doc.folderType = file.getFolderType();
        doc.fileCategory = file.getFileCategory();
        doc.createTime = file.getCreateTime() != null ? file.getCreateTime().getTime() : null;
        doc.lastUpdateTime = file.getLastUpdateTime() != null ? file.getLastUpdateTime().getTime() : null;
        doc.fileSize = file.getFileSize();
        return doc;
    }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public Integer getDelFlag() { return delFlag; }
    public void setDelFlag(Integer delFlag) { this.delFlag = delFlag; }
    public Integer getFolderType() { return folderType; }
    public void setFolderType(Integer folderType) { this.folderType = folderType; }
    public Integer getArchived() { return archived; }
    public void setArchived(Integer archived) { this.archived = archived; }
    public Integer getFileCategory() { return fileCategory; }
    public void setFileCategory(Integer fileCategory) { this.fileCategory = fileCategory; }
    public Long getCreateTime() { return createTime; }
    public void setCreateTime(Long createTime) { this.createTime = createTime; }
    public Long getLastUpdateTime() { return lastUpdateTime; }
    public void setLastUpdateTime(Long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Long getArchivedTime() { return archivedTime; }
    public void setArchivedTime(Long archivedTime) { this.archivedTime = archivedTime; }
}
