package com.smartdrive.auth.controller;

import com.smartdrive.auth.component.RedisComponent;
import com.smartdrive.auth.config.AppConfig;
import com.smartdrive.auth.service.EmailCodeService;
import com.smartdrive.auth.service.UserInfoService;
import com.smartdrive.auth.entity.UserInfo;
import com.smartdrive.auth.util.JwtUtil;
import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.controller.BaseController;
import com.smartdrive.common.dto.LoginResultDto;
import com.smartdrive.common.dto.SessionWebUserDto;
import com.smartdrive.common.dto.UserSpaceDto;
import com.smartdrive.common.enums.VerifyRegexEnum;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.utils.CreateImageCode;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.common.vo.ResponseVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

@RestController
public class AccountController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_TYPE_VALUE = "application/json;charset=UTF-8";

    private final UserInfoService userInfoService;
    private final EmailCodeService emailCodeService;
    private final AppConfig appConfig;
    private final RedisComponent redisComponent;
    private final JwtUtil jwtUtil;

    @Value("${app.dev:false}")
    private boolean devMode;

    public AccountController(UserInfoService userInfoService, EmailCodeService emailCodeService,
                             AppConfig appConfig, RedisComponent redisComponent, JwtUtil jwtUtil) {
        this.userInfoService = userInfoService;
        this.emailCodeService = emailCodeService;
        this.appConfig = appConfig;
        this.redisComponent = redisComponent;
        this.jwtUtil = jwtUtil;
    }

    // ===== 图片验证码 — 完整保留（使用 session 存储没问题，gateway 对此路径不拦截） =====
    @GetMapping("/auth/checkCode")
    public void checkCode(HttpServletResponse response, HttpSession session, Integer type) throws Exception {
        CreateImageCode vCode = new CreateImageCode(130, 38, 5, 10);
        response.setHeader("Pragma", "No-cache");
        response.setHeader("Cache-Control", "No-cache");
        response.setDateHeader("Expires", 0);
        response.setContentType("image/jpeg");
        String code = vCode.getCode();
        if (type == null || type == 0) {
            session.setAttribute(Constants.CHECK_CODE_KEY, code);
        } else {
            session.setAttribute(Constants.CHECK_CODE_KEY_EMAIL, code);
        }
        vCode.write(response.getOutputStream());
    }

    // ===== 发送邮箱验证码 — 完整保留 =====
    @PostMapping("/auth/sendEmailCode")
    public ResponseVO sendEmailCode(HttpSession session,
                                     @RequestParam String email,
                                     @RequestParam String checkCode,
                                     @RequestParam Integer type) {
        try {
            if (!devMode && !checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY_EMAIL))) {
                throw new BusinessException("图片验证码不正确");
            }
            emailCodeService.sendEmailCode(email, type);
            return getSuccessResponseVO(null);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY_EMAIL);
        }
    }

    // ===== 注册 — 完整保留 =====
    @PostMapping("/auth/register")
    public ResponseVO register(HttpSession session,
                                @RequestParam String email,
                                @RequestParam String nickName,
                                @RequestParam String password,
                                @RequestParam String checkCode,
                                @RequestParam String emailCode) {
        try {
            if (!devMode && !checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new BusinessException("图片验证码不正确");
            }
            userInfoService.register(email, nickName, password, emailCode);
            return getSuccessResponseVO(null);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    // ===== 登录 — 架构性改动：返回 JWT =====
    @PostMapping("/auth/login")
    public ResponseVO login(HttpSession session,
                             @RequestParam String email,
                             @RequestParam String password,
                             @RequestParam String checkCode) {
        try {
            if (!devMode && !checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new BusinessException("图片验证码不正确");
            }
            SessionWebUserDto userDto = userInfoService.login(email, password);

            // 生成会话ID，用于防多地登录互踢
            String sessionId = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            redisComponent.saveSessionId(userDto.getUserId(), sessionId);

            // 签发 JWT
            String accessToken = jwtUtil.generateAccessToken(userDto.getUserId(),
                    Boolean.TRUE.equals(userDto.getAdmin()), userDto.getNickName(), userDto.getDepartmentId(), sessionId);
            String refreshToken = jwtUtil.generateRefreshToken(userDto.getUserId(), sessionId);

            LoginResultDto result = new LoginResultDto();
            result.setAccessToken(accessToken);
            result.setRefreshToken(refreshToken);
            result.setUserInfo(userDto);
            return getSuccessResponseVO(result);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    // ===== 重置密码 — 完整保留 =====
    @PostMapping("/auth/resetPwd")
    public ResponseVO resetPwd(HttpSession session,
                                @RequestParam String email,
                                @RequestParam String password,
                                @RequestParam String checkCode,
                                @RequestParam String emailCode) {
        try {
            if (!devMode && !checkCode.equalsIgnoreCase((String) session.getAttribute(Constants.CHECK_CODE_KEY))) {
                throw new BusinessException("图片验证码不正确");
            }
            userInfoService.resetPwd(email, password, emailCode);
            return getSuccessResponseVO(null);
        } finally {
            session.removeAttribute(Constants.CHECK_CODE_KEY);
        }
    }

    // ===== 获取头像 — 完整保留 =====
    @GetMapping("/auth/getAvatar/{userId}")
    public void getAvatar(HttpServletResponse response, @PathVariable String userId) {
        String avatarFolderName = Constants.FILE_FOLDER_FILE + Constants.FILE_FOLDER_AVATAR_NAME;
        File folder = new File(appConfig.getProjectFolder() + avatarFolderName);
        if (!folder.exists()) { folder.mkdirs(); }
        String avatarPath = appConfig.getProjectFolder() + avatarFolderName + "/" + userId + Constants.AVATAR_SUFFIX;
        File file = new File(avatarPath);
        if (!file.exists()) {
            if (!new File(appConfig.getProjectFolder() + avatarFolderName + "/" + Constants.AVATAR_DEFAULT).exists()) {
                response.setHeader(CONTENT_TYPE, CONTENT_TYPE_VALUE);
                response.setStatus(HttpStatus.OK.value());
                try (PrintWriter writer = response.getWriter()) {
                    writer.print("请在头像目录下放置默认头像default_avatar.jpg");
                } catch (Exception ignored) {}
                return;
            }
            avatarPath = appConfig.getProjectFolder() + avatarFolderName + "/" + Constants.AVATAR_DEFAULT;
        }
        response.setContentType("image/jpg");
        readFile(response, avatarPath);
    }

    // ===== 获取用户信息 — 从 Header 读（Gateway 已验签） =====
    @RequestMapping("/auth/getUserInfo")
    public ResponseVO getUserInfo() {
        String userId = getCurrentUserId();
        UserInfo userInfo = userInfoService.getUserInfoByUserId(userId);
        SessionWebUserDto dto = new SessionWebUserDto();
        if (userInfo != null) {
            dto.setUserId(userInfo.getUserId());
            dto.setNickName(userInfo.getNickName());
            dto.setAvatar(userInfo.getAvatar());
            dto.setAdmin(Boolean.TRUE.equals(userInfo.getIsAdmin()));
            dto.setDepartmentId(userInfo.getDepartmentId());
            dto.setDeptHead(userInfoService.isDeptHead(userInfo.getDepartmentId(), userId));
        }
        return getSuccessResponseVO(dto);
    }

    // ===== 获取用户空间 — 从 Header 读 =====
    @PostMapping("/auth/getUseSpace")
    public ResponseVO getUseSpace() {
        String userId = getCurrentUserId();
        UserSpaceDto userSpaceDto = redisComponent.getUserSpace(userId);
        return getSuccessResponseVO(userSpaceDto);
    }

    // ===== 退出登录 =====
    @PostMapping("/auth/logout")
    public ResponseVO logout() {
        // JWT 无状态，客户端清除 token 即可
        return getSuccessResponseVO(null);
    }

    // ===== Token 刷新 =====
    @PostMapping("/auth/refresh")
    public ResponseVO refreshToken(@RequestParam String refreshToken) {
        try {
            var claims = jwtUtil.parseToken(refreshToken);
            String userId = claims.getSubject();

            // 黑名单检查：若 token 签发时间早于列入黑名单时间，拒绝续期
            Long banTimestamp = redisComponent.getBlacklistTimestamp(userId);
            if (banTimestamp != null) {
                long iatSeconds = claims.getIssuedAt().toInstant().getEpochSecond();
                if (iatSeconds < banTimestamp) {
                    throw new BusinessException(com.smartdrive.common.enums.ResponseCodeEnum.CODE_901);
                }
            }

            UserInfo userInfo = userInfoService.getUserInfoByUserId(userId);
            if (userInfo == null) { throw new BusinessException("用户不存在"); }
            boolean isAdmin = userInfoService.isAdmin(userId);
            String nickName = userInfo.getNickName();
            String deptId = userInfo.getDepartmentId();
            // 从旧 refreshToken 取出 sessionId，续期不产生新会话
            String sessionId = claims.get("sessionId", String.class);
            if (sessionId == null) { sessionId = ""; }
            String newAccessToken = jwtUtil.generateAccessToken(userId, isAdmin, nickName, deptId, sessionId);
            Map<String, String> result = new HashMap<>();
            result.put("accessToken", newAccessToken);
            return getSuccessResponseVO(result);
        } catch (Exception e) {
            throw new BusinessException(com.smartdrive.common.enums.ResponseCodeEnum.CODE_901);
        }
    }

    // ===== 上传头像 — 完整保留 =====
    @PostMapping("/auth/updateUserAvatar")
    public ResponseVO updateUserAvatar(HttpServletRequest request) {
        String userId = getCurrentUserId();
        String baseFolder = appConfig.getProjectFolder() + Constants.FILE_FOLDER_FILE;
        File targetFileFolder = new File(baseFolder + Constants.FILE_FOLDER_AVATAR_NAME);
        if (!targetFileFolder.exists()) { targetFileFolder.mkdirs(); }
        File targetFile = new File(targetFileFolder.getPath() + "/" + userId + Constants.AVATAR_SUFFIX);
        try {
            // multipart 上传
            var part = request.getPart("avatar");
            if (part != null) {
                part.write(targetFile.getAbsolutePath());
            }
        } catch (Exception e) {
            logger.error("上传头像失败", e);
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setAvatar("");
        userInfoService.updateUserInfoByUserId(userInfo, userId);
        return getSuccessResponseVO(null);
    }

    // ===== 部门成员列表（搜索的上传者筛选用）：非管理员固定本部门，不可横向枚举 =====
    @RequestMapping("/auth/deptMembers")
    public ResponseVO deptMembers(@RequestParam(required = false) String departmentId) {
        String deptId = (isAdmin() && departmentId != null && !departmentId.isEmpty())
                ? departmentId : getCurrentUserDepartmentId();
        if (deptId == null || deptId.isEmpty()) { return getSuccessResponseVO(java.util.List.of()); }
        java.util.List<Map<String, String>> result = userInfoService.getDepartmentMembers(deptId).stream()
                .map(u -> Map.of(
                        "userId", u.getUserId(),
                        "nickName", u.getNickName() != null ? u.getNickName() : u.getUserId()))
                .collect(java.util.stream.Collectors.toList());
        return getSuccessResponseVO(result);
    }

    // ===== 修改密码 — 完整保留 =====
    @PostMapping("/auth/updatePassword")
    public ResponseVO updatePassword(@RequestParam String password) {
        String userId = getCurrentUserId();
        UserInfo userInfo = new UserInfo();
        userInfo.setPassword(StringTools.encodeByMd5(password));
        userInfoService.updateUserInfoByUserId(userInfo, userId);
        return getSuccessResponseVO(null);
    }
}
