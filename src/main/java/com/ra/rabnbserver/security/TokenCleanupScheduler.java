package com.ra.rabnbserver.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理过期 token
 */
@Slf4j(topic = "com.ra.rabnbserver.service.token")
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final TokenStore tokenStore;

    /**
     * 600 秒清理一次（可通过配置覆盖）
     */
    @Scheduled(fixedDelayString = "${token-cache.cleanup-seconds:600}000")
    public void cleanup() {
        int removed = tokenStore.cleanupExpired();
        if (removed > 0) {
            log.info("清理过期 token 完成，已移除数量={}", removed);
        }
    }
}
