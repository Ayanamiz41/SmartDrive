package com.smartdrive.auth.service.impl;

import com.smartdrive.auth.component.RedisComponent;
import com.smartdrive.auth.config.AppConfig;
import com.smartdrive.auth.service.EmailCodeService;
import com.smartdrive.common.dto.SysSettingDto;
import com.smartdrive.common.exception.BusinessException;
import com.smartdrive.common.utils.StringTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.Date;

@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    private final JavaMailSender javaMailSender;
    private final AppConfig appConfig;
    private final RedisComponent redisComponent;

    @Value("${app.dev:false}")
    private boolean devMode;

    private static final Logger logger = LoggerFactory.getLogger(EmailCodeServiceImpl.class);

    public EmailCodeServiceImpl(JavaMailSender javaMailSender,
                                AppConfig appConfig, RedisComponent redisComponent) {
        this.javaMailSender = javaMailSender;
        this.appConfig = appConfig;
        this.redisComponent = redisComponent;
    }

    @Override
    public void sendEmailCode(String email, Integer type) {
        if (type == null) type = 0;
        String code = StringTools.getRandomNumber(5);
        if (!devMode) {
            sendEmail(email, code);
        } else {
            code = "00000";
        }
        redisComponent.saveEmailCode(email, code);
    }

    private void sendEmail(String toEmail, String code) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(appConfig.getSendUserName());
            helper.setTo(toEmail);

            SysSettingDto sysSettingDto = redisComponent.getSysSettingDto();
            helper.setSubject(sysSettingDto.getRegisterEmailTitle());
            helper.setText(String.format(sysSettingDto.getRegisterEmailContent(), code));
            helper.setSentDate(new Date());
            javaMailSender.send(message);
        } catch (Exception e) {
            logger.error("邮件发送失败", e);
            throw new BusinessException("邮件发送失败");
        }
    }

    @Override
    public void checkCode(String email, String code) {
        if (devMode) return;
        String stored = redisComponent.getEmailCode(email);
        if (stored == null) {
            throw new BusinessException("验证码已过期，请重新发送");
        }
        if (!stored.equals(code)) {
            throw new BusinessException("邮箱验证码不正确");
        }
        redisComponent.deleteEmailCode(email);
    }
}
