package com.smartdrive.file.service;

import com.smartdrive.common.dto.SessionWebUserDto;
import com.smartdrive.common.dto.UploadResultDto;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.file.entity.FileInfo;
import com.smartdrive.file.entity.query.FileInfoQuery;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileInfoService {

    List<FileInfo> findListByParam(FileInfoQuery query);

    Integer findCountByParam(FileInfoQuery query);

    PaginationResultVO<FileInfo> findListByPage(FileInfoQuery query);

    Integer add(FileInfo bean);

    Integer addBatch(List<FileInfo> listBean);

    Integer addOrUpdateBatch(List<FileInfo> listBean);

    FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId);
    Long getUsedSpace(String userId);
    FileInfo getFileInfoByFileId(String fileId);

    Integer updateFileInfoByFileIdAndUserId(FileInfo bean, String fileId, String userId);

    Integer updateFileInfoByFileId(FileInfo bean, String fileId);

    Integer deleteFileInfoByFileIdAndUserId(String fileId, String userId);

    UploadResultDto uploadFile(SessionWebUserDto sessionWebUserDto, String fileId, MultipartFile file,
                                String fileName, String filePid, String fileMd5,
                                Integer chunkIndex, Integer chunks);

    FileInfo newFolder(String filePid, String userId, String folderName, String departmentId, boolean autoRename);

    FileInfo rename(String fileId, String userId, String newName, boolean autoRename);

    List<FileInfo> loadAllFolder(String userId, String filePid, String currentFolderIds);

    void changeFileFolder(String userId, String fileIds, String filePid, boolean isAdmin);

    void removeFile2RecycleBatch(String userId, String fileIds, boolean isAdmin);

    void recoverFileBatch(String userId, String fileIds);
    void recoverFileById(String userId, String fileId);

    void delFileBatch(String userId, String fileIds, Boolean admin);
    void delFileById(String userId, String fileId);

    void checkRootFilePid(String rootFilePid, String userId, String fileId);

    void saveShare(String shareRootFileId, String shareFileIds, String myFolderId,
                   String shareUserId, String targetDepartmentId, String currentUserId);

    void archiveFile(String userId, String fileIds, boolean isAdmin);
    void unarchiveFile(String userId, String fileIds, boolean isAdmin);

    /** 拷贝文件到其他空间（个人→部门 或 部门→个人），增加当前用户空间占用 */
    void copyFile(String fileId, String targetFolderId, String userId, String targetDepartmentId);
}
