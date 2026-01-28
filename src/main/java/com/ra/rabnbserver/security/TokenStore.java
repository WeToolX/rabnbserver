package com.ra.rabnbserver.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token 白名单（单账号单 token）
 */
@Slf4j(topic = "com.ra.rabnbserver.service.token")
@Component
public class TokenStore {

    private final Map<String, TokenEntry> tokenMap = new ConcurrentHashMap<>();
    private final Map<String, String> subjectToToken = new ConcurrentHashMap<>();

    /**
     * 写入白名单（同账号只保留最后一次登录）
     */
    public void storeToken(String subject, String token, long expireAtMillis) {
        if (subject == null || subject.isBlank() || token == null || token.isBlank()) {
            log.warn("写入白名单失败，登录标识或 token 为空");
            return;
        }
        String oldToken = subjectToToken.put(subject, token);
        if (oldToken != null && !oldToken.equals(token)) {
            tokenMap.remove(oldToken);
        }
        tokenMap.put(token, new TokenEntry(subject, expireAtMillis));
        log.info("写入白名单成功，登录标识={}, 过期时间={}", subject, Instant.ofEpochMilli(expireAtMillis));
    }

    /**
     * 校验 token 是否在白名单中
     */
    public boolean isTokenAllowed(String subject, String token) {
        if (subject == null || token == null) {
            return false;
        }
        TokenEntry entry = tokenMap.get(token);
        if (entry == null) {
            return false;
        }
        if (!subject.equals(entry.getSubject())) {
            return false;
        }
        String currentToken = subjectToToken.get(subject);
        if (!token.equals(currentToken)) {
            return false;
        }
        if (entry.getExpireAtMillis() <= System.currentTimeMillis()) {
            removeToken(token, subject);
            return false;
        }
        return true;
    }

    /**
     * 清理过期 token
     */
    public int cleanupExpired() {
        int removed = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, TokenEntry> entry : tokenMap.entrySet()) {
            if (entry.getValue().getExpireAtMillis() <= now) {
                removeToken(entry.getKey(), entry.getValue().getSubject());
                removed++;
            }
        }
        return removed;
    }

    private void removeToken(String token, String subject) {
        tokenMap.remove(token);
        if (subject != null) {
            subjectToToken.remove(subject, token);
        }
    }

    /**
     * token 记录
     */
    @Getter
    public static class TokenEntry {
        private final String subject;
        private final long expireAtMillis;

        public TokenEntry(String subject, long expireAtMillis) {
            this.subject = subject;
            this.expireAtMillis = expireAtMillis;
        }
    }
}
