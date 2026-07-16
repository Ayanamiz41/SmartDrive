package com.smartdrive.common.controller;

import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.dto.SessionShareDto;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.common.vo.PaginationResultVO;
import com.smartdrive.common.vo.ResponseVO;
import com.smartdrive.common.utils.CopyTools;
import com.smartdrive.common.utils.StringTools;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

public class BaseController {

    protected static final String STATUS_SUCCESS = "success";
    protected static final String STATUS_ERROR = "error";

    private static final Logger logger = LoggerFactory.getLogger(BaseController.class);

    protected <T> ResponseVO getSuccessResponseVO(T t) {
        ResponseVO<T> responseVO = new ResponseVO<>();
        responseVO.setStatus(STATUS_SUCCESS);
        responseVO.setCode(ResponseCodeEnum.CODE_200.getCode());
        responseVO.setInfo(ResponseCodeEnum.CODE_200.getMsg());
        responseVO.setData(t);
        return responseVO;
    }

    protected void readFile(HttpServletResponse response, String filePath) {
        if (!StringTools.pathIsOk(filePath)) { return; }
        OutputStream out = null;
        FileInputStream fin = null;
        try {
            File file = new File(filePath);
            if (!file.exists()) { return; }
            fin = new FileInputStream(file);
            byte[] byteData = new byte[1024];
            out = response.getOutputStream();
            int len = 0;
            while ((len = fin.read(byteData)) != -1) {
                out.write(byteData, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            logger.error("读取文件异常", e);
        } finally {
            if (out != null) { try { out.close(); } catch (IOException e) { logger.error("IO异常", e); } }
            if (fin != null) { try { fin.close(); } catch (IOException e) { logger.error("IO异常", e); } }
        }
    }

    /** 从 Gateway 透传的 Header 获取当前用户 ID */
    protected String getCurrentUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return attrs.getRequest().getHeader("X-User-Id");
    }

    /** 判断当前用户是否为管理员 */
    protected boolean isAdmin() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return false;
        return "admin".equals(attrs.getRequest().getHeader("X-User-Role"));
    }

    /** 获取当前用户昵称 */
    protected String getCurrentUserNickName() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return attrs.getRequest().getHeader("X-User-NickName");
    }

    /** 获取当前用户部门ID */
    protected String getCurrentUserDepartmentId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        String deptId = attrs.getRequest().getHeader("X-User-DepartmentId");
        return (deptId == null || deptId.isEmpty()) ? null : deptId;
    }

    // === 以下方法保留用于 share-service 的 session 分享校验（Gateway 不拦截外部分享） ===

    protected <S, T> PaginationResultVO<T> convert2PaginationVO(PaginationResultVO<S> result, Class<T> classz) {
        PaginationResultVO<T> paginationResultVO = new PaginationResultVO<>();
        paginationResultVO.setList(CopyTools.copyList(result.getList(), classz));
        paginationResultVO.setPageNo(result.getPageNo());
        paginationResultVO.setPageSize(result.getPageSize());
        paginationResultVO.setPageTotal(result.getPageTotal());
        paginationResultVO.setTotalCount(result.getTotalCount());
        return paginationResultVO;
    }

    protected SessionShareDto getSessionShareFromSession(HttpSession session, String shareId) {
        return (SessionShareDto) session.getAttribute(Constants.SESSION_SHARE_KEY + shareId);
    }
}
