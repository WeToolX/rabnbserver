package com.ra.rabnbserver.exception.Abnormal.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 异常重试框架配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "abnormal.retry")
public class AbnormalRetryProperties {

    /**
     * 轮询间隔（秒），默认 300 秒
     */
    private int scanIntervalSeconds = 300;

    /**
     * 是否启用邮件通知
     */
    private boolean mailEnabled = true;

    /**
     * 发件人邮箱
     */
    private String mailFrom;

    /**
     * 收件人邮箱列表
     */
    private List<String> mailTo;
}
