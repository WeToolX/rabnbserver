package com.ra.rabnbserver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * JWT 生成与解析服务
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT
     *
     * @param subject 主题（用户唯一标识）
     * @param claims  自定义负载（可为空）
     * @return token 字符串
     */
    public String generateToken(String subject, Map<String, Object> claims) {
        Instant now = Instant.now();
        Instant expireAt = now.plusSeconds(jwtProperties.getExpireHours() * 3600L);
        var builder = Jwts.builder()
                .subject(subject)
                .claims(claims == null ? Map.of() : claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(getSecretKey(), SignatureAlgorithm.HS256);
        if (jwtProperties.getIssuer() != null && !jwtProperties.getIssuer().isBlank()) {
            builder.issuer(jwtProperties.getIssuer());
        }
        return builder.compact();
    }

    /**
     * 获取 token 过期秒数
     */
    public long getExpireSeconds() {
        return jwtProperties.getExpireHours() * 3600L;
    }

    /**
     * 解析并验证 JWT
     *
     * @param token JWT
     * @return Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
