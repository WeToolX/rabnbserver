package com.ra.rabnbserver.contract.support;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 合约管理员私钥配置（密文）
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "contract.admin")
public class ContractAdminProperties {

    /**
     * 管理员私钥密文（Base64，格式：ivBase64:cipherBase64）
     */
    @NotBlank(message = "contract.admin.private-key-enc 不能为空")
    private String privateKeyEnc;
}
