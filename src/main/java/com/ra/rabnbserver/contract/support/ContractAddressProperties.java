package com.ra.rabnbserver.contract.support;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 合约地址配置
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "contract.address")
public class ContractAddressProperties {

    /**
     * 地址格式（0x + 40 位十六进制）
     */
    private static final String ADDRESS_REGEX = "^0x[0-9a-fA-F]{40}$";

    /**
     * PaymentUSDT 合约地址
     */
    @NotBlank(message = "contract.address.payment-usdt 不能为空")
    @Pattern(regexp = ADDRESS_REGEX, message = "contract.address.payment-usdt 必须为 0x 开头的 40 位十六进制地址")
    private String paymentUsdt;

    /**
     * CardNFT 合约地址
     */
    @NotBlank(message = "contract.address.card-nft 不能为空")
    @Pattern(regexp = ADDRESS_REGEX, message = "contract.address.card-nft 必须为 0x 开头的 40 位十六进制地址")
    private String cardNft;

    /**
     * AION 合约地址
     */
    @NotBlank(message = "contract.address.aion 不能为空")
    @Pattern(regexp = ADDRESS_REGEX, message = "contract.address.aion 必须为 0x 开头的 40 位十六进制地址")
    private String aion;

    /**
     * 启动时输出合约地址，便于排查配置问题
     */
    @PostConstruct
    public void logContractAddresses() {
        log.info("PaymentUSDT 合约地址: {}", paymentUsdt);
        log.info("CardNFT 合约地址: {}", cardNft);
        log.info("AION 合约地址: {}", aion);
    }
}
