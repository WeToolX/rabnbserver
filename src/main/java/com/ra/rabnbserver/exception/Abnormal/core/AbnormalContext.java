package com.ra.rabnbserver.exception.Abnormal.core;

import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import lombok.Getter;

/**
 * 异常重试上下文（注解配置 + 处理器实例）
 */
@Getter
public class AbnormalContext {

    private final AbnormalRetryConfig config;
    private final AbnormalRetryHandler handler;
    private final Class<?> targetClass;

    public AbnormalContext(AbnormalRetryConfig config, AbnormalRetryHandler handler, Class<?> targetClass) {
        this.config = config;
        this.handler = handler;
        this.targetClass = targetClass;
    }

    public String table() {
        return config.table();
    }
}
