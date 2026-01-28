package com.ra.rabnbserver.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 统一的 Sa-Token 提取器
 */
@Component
public class TokenExtractor {

    /**
     * 从请求头中提取 Sa-Token
     * 支持：
     * - Account-token: <token>
     */
    public String extract(HttpServletRequest request) {
        String accountToken = request.getHeader("Account-token");
        if (StringUtils.hasText(accountToken)) {
            return accountToken.trim();
        }
        return null;
    }
}
