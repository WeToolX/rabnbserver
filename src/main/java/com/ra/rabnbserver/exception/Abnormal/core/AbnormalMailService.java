package com.ra.rabnbserver.exception.Abnormal.core;

import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 异常通知邮件服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AbnormalMailService {

    private final AbnormalRetryProperties properties;
    private final ObjectProvider<ObjectMapper> objectMapperProvider;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;

    /**
     * 发送异常通知邮件
     *
     * @param config 注解配置
     * @param record 异常记录
     * @param fullData 数据库完整数据
     */
    public void sendErrToMail(AbnormalRetryConfig config, AbnormalRecord record, Map<String, Object> fullData) {
        if (!properties.isMailEnabled()) {
            log.warn("异常通知邮件未启用，已跳过发送，服务={}", config.serviceName());
            return;
        }
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        if (javaMailSender == null) {
            throw new IllegalStateException("邮件发送器未注入，请检查 mail 依赖与配置");
        }
        String from = properties.getMailFrom();
        List<String> toList = properties.getMailTo();
        if (from == null || from.isBlank() || toList == null || toList.isEmpty()) {
            throw new IllegalStateException("邮件配置不完整，请补充 abnormal.retry.mail-from 与 abnormal.retry.mail-to");
        }
        String subject = "【" + config.serviceName() + "】异常自动处理超限，请人工处理";
        String body = buildMailBody(record, fullData);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toList.toArray(new String[0]));
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
        log.info("异常通知邮件发送成功，服务={}, 数据ID={}", config.serviceName(), record.getId());
    }

    private String buildMailBody(AbnormalRecord record, Map<String, Object> fullData) {
        StringBuilder sb = new StringBuilder();
        sb.append("异常用户：").append(record.getUserValue()).append("\n");
        sb.append("异常开始时间：").append(record.getErrStartTime()).append("\n");
        sb.append("已重试次数：").append(record.getErrRetryCount()).append("\n");
        sb.append("通知人工处理次数：").append(record.getErrManualNotifyCount()).append("\n");
        sb.append("异常持续时长：").append(calcDurationSeconds(record.getErrStartTime())).append(" 秒\n\n");
        sb.append("完整数据：\n");
        try {
            ObjectMapper objectMapper = objectMapperProvider.getIfAvailable();
            if (objectMapper == null) {
                objectMapper = new ObjectMapper();
            }
            sb.append(objectMapper.writeValueAsString(fullData));
        } catch (Exception e) {
            sb.append(fullData);
        }
        return sb.toString();
    }

    private long calcDurationSeconds(LocalDateTime startTime) {
        if (startTime == null) {
            return -1L;
        }
        return Duration.between(startTime, LocalDateTime.now()).getSeconds();
    }
}
