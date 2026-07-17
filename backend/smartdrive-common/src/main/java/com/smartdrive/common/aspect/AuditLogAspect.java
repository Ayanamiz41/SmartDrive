package com.smartdrive.common.aspect;

import com.smartdrive.common.annotation.AuditLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);
    private final DataSource dataSource;

    public AuditLogAspect(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        String userId = null;
        String userName = null;
        String targetId = null;
        String targetName = null;
        Map<String, String> nameMap = null;
        Map<String, String> deptMap = null;

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                userId = attrs.getRequest().getHeader("X-User-Id");
                String encodedName = attrs.getRequest().getHeader("X-User-NickName");
                if (encodedName != null) {
                    userName = java.net.URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
                }
            }
            targetId = extractParam(pjp, auditLog.targetIdParam());
            targetName = extractParam(pjp, auditLog.targetNameParam());

            // 方法执行前提前查好文件名和部门（避免异步查不到已删除/已变更的文件）
            String[] ids = splitIds(targetId);
            if (ids.length > 0) {
                String type = auditLog.targetType().name();
                nameMap = resolveFileNames(type, ids);
                deptMap = resolveFileDeptIds(type, ids);
            }
        } catch (Exception e) {
            log.warn("审计参数提取失败: {}", e.getMessage());
        }

        // 执行原方法
        Object result = pjp.proceed();

        // 异步写审计日志
        if (userId != null) {
            asyncWrite(userId, userName, auditLog.value().name(),
                    auditLog.targetType().name(), targetId, targetName, nameMap, deptMap);
        }

        return result;
    }

    @Async
    public void asyncWrite(String userId, String userName, String action,
                           String targetType, String targetId, String targetName,
                           Map<String, String> nameMap, Map<String, String> deptMap) {
        String[] ids = splitIds(targetId);
        if (ids.length == 0) return;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO audit_log (user_id, user_name, action, target_type, target_id, target_name, created_at, department_id) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (String id : ids) {
                String name = (nameMap != null) ? nameMap.getOrDefault(id,
                        targetName != null && !targetName.equals(id) ? targetName : "") : "";
                String dept = (deptMap != null) ? deptMap.get(id) : null;
                ps.setString(1, userId);
                ps.setString(2, userName != null ? userName : "");
                ps.setString(3, action);
                ps.setString(4, targetType);
                ps.setString(5, id);
                ps.setString(6, name != null ? name : "");
                ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
                ps.setString(8, dept);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (Exception e) {
            log.warn("审计日志写入失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String[] splitIds(String targetId) {
        if (targetId == null) return new String[0];
        String[] raw = targetId.contains(",") ? targetId.split(",") : new String[]{targetId};
        return java.util.Arrays.stream(raw)
                .filter(id -> id != null && !id.trim().isEmpty())
                .map(String::trim).toArray(String[]::new);
    }

    private Map<String, String> resolveFileNames(String targetType, String[] ids) {
        return resolveField("file_name", ids);
    }

    private Map<String, String> resolveFileDeptIds(String targetType, String[] ids) {
        return resolveField("department_id", ids);
    }

    private Map<String, String> resolveField(String column, String[] ids) {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < ids.length; i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String sql = "SELECT file_id, " + column + " FROM file_info WHERE file_id IN (" + placeholders + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < ids.length; i++) {
                    ps.setString(i + 1, ids[i]);
                }
                var rs = ps.executeQuery();
                while (rs.next()) {
                    String val = rs.getString(column);
                    if (val != null) result.put(rs.getString("file_id"), val);
                }
            }
        } catch (Exception e) {
            log.warn("审计解析 {} 失败: {}", column, e.getMessage());
        }
        return result;
    }

    private String extractParam(ProceedingJoinPoint pjp, String paramName) {
        if (paramName == null || paramName.isEmpty()) return null;
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Parameter[] params = method.getParameters();
        Object[] args = pjp.getArgs();
        for (int i = 0; i < params.length; i++) {
            if (paramName.equals(params[i].getName())) {
                Object val = args[i];
                return val != null ? val.toString() : null;
            }
        }
        return null;
    }
}
