package com.smartdrive.auth.service.impl;

import com.smartdrive.auth.component.RedisComponent;
import com.smartdrive.auth.config.AppConfig;
import com.smartdrive.auth.entity.UserInfo;
import com.smartdrive.auth.entity.query.UserInfoQuery;
import com.smartdrive.auth.mapper.UserInfoMapper;
import com.smartdrive.auth.service.EmailCodeService;
import com.smartdrive.auth.service.UserInfoService;
import com.smartdrive.auth.feign.FileFeignClient;
import com.smartdrive.common.constant.Constants;
import com.smartdrive.common.dto.QQInfoDto;
import com.smartdrive.common.dto.SessionWebUserDto;
import com.smartdrive.common.dto.SysSettingDto;
import com.smartdrive.common.dto.UserSpaceDto;
import com.smartdrive.common.enums.PageSize;
import com.smartdrive.common.enums.ResponseCodeEnum;
import com.smartdrive.common.enums.UserStatusEnum;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.query.SimplePage;
import com.smartdrive.common.utils.JsonUtils;
import com.smartdrive.common.utils.OKHttpUtils;
import com.smartdrive.common.utils.StringTools;
import com.smartdrive.common.vo.PaginationResultVO;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoMapper userInfoMapper;
    private final EmailCodeService emailCodeService;
    private final RedisComponent redisComponent;
    private final AppConfig appConfig;
    private final javax.sql.DataSource dataSource;
    private final FileFeignClient fileFeignClient;

    private static final Logger logger = LoggerFactory.getLogger(UserInfoServiceImpl.class);

    public UserInfoServiceImpl(UserInfoMapper userInfoMapper, EmailCodeService emailCodeService,
                               RedisComponent redisComponent, AppConfig appConfig,
                               javax.sql.DataSource dataSource, FileFeignClient fileFeignClient) {
        this.userInfoMapper = userInfoMapper;
        this.emailCodeService = emailCodeService;
        this.redisComponent = redisComponent;
        this.appConfig = appConfig;
        this.dataSource = dataSource;
        this.fileFeignClient = fileFeignClient;
    }

    @Override
    public List<UserInfo> findListByParam(UserInfoQuery query) { return userInfoMapper.selectList(query); }

    @Override
    public Integer findCountByParam(UserInfoQuery query) { return userInfoMapper.selectCount(query); }

    @Override
    public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery query) {
        Integer count = findCountByParam(query);
        Integer pageSize = query.getPageSize() == null ? PageSize.SIZE15.getSize() : query.getPageSize();
        SimplePage page = new SimplePage(query.getPageNo(), count, pageSize);
        query.setSimplePage(page);
        List<UserInfo> list = findListByParam(query);
        return new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(), page.getPageTotal(), list);
    }

    @Override
    public Integer add(UserInfo bean) { return userInfoMapper.insert(bean); }

    @Override
    public Integer addBatch(List<UserInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) return 0;
        return userInfoMapper.insertBatch(listBean);
    }

    @Override
    public Integer addOrUpdateBatch(List<UserInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) return 0;
        return userInfoMapper.insertOrUpdateBatch(listBean);
    }

    @Override
    public UserInfo getUserInfoByUserId(String userId) { return userInfoMapper.selectByUserId(userId); }

    @Override
    public Integer updateUserInfoByUserId(UserInfo bean, String userId) { return userInfoMapper.updateByUserId(bean, userId); }

    @Override
    public Integer deleteUserInfoByUserId(String userId) { return userInfoMapper.deleteByUserId(userId); }

    @Override
    public UserInfo getUserInfoByEmail(String email) { return userInfoMapper.selectByEmail(email); }

    @Override
    public Integer updateUserInfoByEmail(UserInfo bean, String email) { return userInfoMapper.updateByEmail(bean, email); }

    @Override
    public Integer deleteUserInfoByEmail(String email) { return userInfoMapper.deleteByEmail(email); }

    @Override
    public UserInfo getUserInfoByNickName(String nickName) { return userInfoMapper.selectByNickName(nickName); }

    @Override
    public Integer updateUserInfoByNickName(UserInfo bean, String nickName) { return userInfoMapper.updateByNickName(bean, nickName); }

    @Override
    public Integer deleteUserInfoByNickName(String nickName) { return userInfoMapper.deleteByNickName(nickName); }

    // ===================================================================
    // 核心算法：注册 — 完整保留
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickName, String password, String emailCode) {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo != null) { throw new BusinessException("邮箱账号已存在"); }

        UserInfo nickNameUser = userInfoMapper.selectByNickName(nickName);
        if (nickNameUser != null) { throw new BusinessException("昵称已存在"); }

        emailCodeService.checkCode(email, emailCode);

        String userId = StringTools.getRandomNumber(Constants.LENGTH_10);
        userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setEmail(email);
        userInfo.setNickName(nickName);
        userInfo.setPassword(StringTools.encodeByMd5(password));
        userInfo.setJoinTime(new Date());
        userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
        userInfo.setUseSpace(0L);
        SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
        userInfo.setTotalSpace(sysSettingDto.getUserInitUseSpace() * Constants.MB);
        userInfoMapper.insert(userInfo);
    }

    // ===================================================================
    // 核心算法：登录 — 保留原逻辑，架构性改动：返回 SessionWebUserDto（JWT 在 Controller 层签发）
    // ===================================================================
    @Override
    public SessionWebUserDto login(String email, String password) {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo == null || !userInfo.getPassword().equals(password)) {
            throw new BusinessException("账号或者密码错误");
        }
        if (userInfo.getStatus().equals(UserStatusEnum.DISABLE.getStatus())) {
            throw new BusinessException("账号已被禁用");
        }

        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastLoginTime(new Date());
        userInfoMapper.updateByUserId(updateInfo, userInfo.getUserId());

        SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
        sessionWebUserDto.setNickName(userInfo.getNickName());
        sessionWebUserDto.setUserId(userInfo.getUserId());
        sessionWebUserDto.setAvatar(userInfo.getAvatar());
        sessionWebUserDto.setDepartmentId(userInfo.getDepartmentId());
        sessionWebUserDto.setDeptHead(isDeptHead(userInfo.getDepartmentId(), userInfo.getUserId()));
        sessionWebUserDto.setAdmin(Boolean.TRUE.equals(userInfo.getIsAdmin()));

        UserSpaceDto spaceDto = new UserSpaceDto();
        spaceDto.setTotalSpace(userInfo.getTotalSpace());
        spaceDto.setUseSpace(userInfo.getUseSpace());
        redisComponent.saveUserSpace(userInfo.getUserId(), spaceDto);

        return sessionWebUserDto;
    }

    // ===================================================================
    // 核心算法：重置密码 — 完整保留
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(String email, String password, String emailCode) {
        UserInfo userInfo = userInfoMapper.selectByEmail(email);
        if (userInfo == null) { throw new BusinessException("账号不存在"); }
        emailCodeService.checkCode(email, emailCode);
        redisComponent.addToBlacklist(userInfo.getUserId());
        UserInfo updateInfo = new UserInfo();
        updateInfo.setPassword(StringTools.encodeByMd5(password));
        userInfoMapper.updateByEmail(updateInfo, email);
    }

    /** 判定用户是否其所在部门的主管（登录/getUserInfo 时写入会话，供前端控制分享入口显隐） */
    @Override
    public boolean isDeptHead(String deptId, String userId) {
        if (deptId == null || deptId.isEmpty()) { return false; }
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement("SELECT COUNT(1) FROM department WHERE id = ? AND head_user_id = ?")) {
            ps.setString(1, deptId);
            ps.setString(2, userId);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            logger.warn("查询部门主管身份失败: userId={}", userId, e);
            return false;
        }
    }

    // ===================================================================
    // 核心算法：更改用户状态 — 保留原逻辑
    // 注意：删除文件的跨服务调用通过 auth-service 直接操作 file_info 表
    // 如果启用 Feign，这里可以调用 file-service 的 deleteFileByUserId
    // ===================================================================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(String userId, Integer status) {
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        if (userInfo == null) { throw new BusinessException(ResponseCodeEnum.CODE_600); }
        UserInfo updateUserInfo = new UserInfo();
        updateUserInfo.setStatus(UserStatusEnum.getByStatus(status).getStatus());
        if (status.equals(UserStatusEnum.DISABLE.getStatus())) {
            redisComponent.addToBlacklist(userId);
        }
        userInfoMapper.updateByUserId(updateUserInfo, userId);
    }

    // ===================================================================
    // 核心算法：更改用户空间 — 完整保留
    // ===================================================================
    @Override
    public void updateUserSpace(String userId, Long changeSpace) {
        Long space = changeSpace * Constants.MB;
        UserInfo userInfo = userInfoMapper.selectByUserId(userId);
        if (space < userInfo.getUseSpace()) { throw new BusinessException("总空间不能小于已使用空间"); }
        userInfoMapper.updateUserSpace(userId, null, space);
        UserSpaceDto userSpaceDto = new UserSpaceDto();
        userSpaceDto.setTotalSpace(space);
        userSpaceDto.setUseSpace(userInfo.getUseSpace());
        redisComponent.saveUserSpace(userId, userSpaceDto);
    }

    @Override
    public void assignDepartment(String userId, String departmentId) {
        UserInfo user = userInfoMapper.selectByUserId(userId);
        if (user == null) throw new BusinessException("用户不存在");
        redisComponent.addToBlacklist(userId);
        userInfoMapper.updateUserDepartment(userId, departmentId);
    }

    @Override
    public String getUserDepartmentId(String userId) {
        UserInfo user = userInfoMapper.selectByUserId(userId);
        return user != null ? user.getDepartmentId() : null;
    }

    @Override
    public boolean isAdmin(String userId) {
        UserInfo user = userInfoMapper.selectByUserId(userId);
        return user != null && Boolean.TRUE.equals(user.getIsAdmin());
    }

    @Override
    public Long getRealUseSpace(String userId) {
        try {
            return fileFeignClient.getUserUsedSpace(userId);
        } catch (Exception e) {
            logger.warn("Feign getUserUsedSpace failed: {}", e.getMessage());
            return 0L;
        }
    }

    @Override
    public void updateUseSpace(String userId, Long useSpace) {
        userInfoMapper.updateUserSpaceAndUsed(userId, useSpace, null);
    }

    @Override
    public void incrementUseSpace(String userId, Long delta) {
        userInfoMapper.updateUserSpace(userId, delta, null);
    }

    @Override
    public List<String> getDepartmentMemberIds(String departmentId) {
        return userInfoMapper.selectByDepartmentId(departmentId).stream()
                .map(UserInfo::getUserId).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public void clearDepartmentMembers(String departmentId) {
        userInfoMapper.selectByDepartmentId(departmentId).forEach(u -> {
            redisComponent.addToBlacklist(u.getUserId());
            userInfoMapper.updateUserDepartment(u.getUserId(), null);
        });
    }

    @Override
    public List<UserInfo> getDepartmentMembers(String departmentId) {
        return userInfoMapper.selectByDepartmentId(departmentId);
    }
}
