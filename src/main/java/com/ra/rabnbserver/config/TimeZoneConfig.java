package com.ra.rabnbserver.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * 全局时区配置（统一 Asia/Shanghai）
 */
@Configuration
public class TimeZoneConfig {

    /**
     * 启动时设置 JVM 默认时区，保证时间类与日志一致
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }
}
