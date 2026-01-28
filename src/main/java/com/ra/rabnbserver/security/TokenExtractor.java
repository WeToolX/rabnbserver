package com.ra.rabnbserver.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 统一的 Token 提取器
 */
@Component
public class TokenExtractor {

    /**
     * 从请求头中提取 JWT
     * 支持：
     * - Authorization: Bearer <token>
     * - Account-token: <token>
     */
    public String extract(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        String accountToken = request.getHeader("Account-token");
        if (StringUtils.hasText(accountToken)) {
            return accountToken.trim();
        }
        return null;
    }
}
