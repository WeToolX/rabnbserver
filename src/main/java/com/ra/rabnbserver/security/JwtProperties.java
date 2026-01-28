package com.ra.rabnbserver.security;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置属性
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT 密钥（至少 32 位字符，保证 HS256 强度）
     */
    @NotBlank(message = "jwt.secret 不能为空")
    private String secret;

    /**
     * 过期时间（小时）
     */
    @Min(value = 1, message = "jwt.expire-hours 必须大于等于 1")
    private long expireHours;

    /**
     * 签发者（可选）
     */
    private String issuer;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("jwt.secret 长度不足 32 位，请设置更安全的密钥");
        }
    }
}
