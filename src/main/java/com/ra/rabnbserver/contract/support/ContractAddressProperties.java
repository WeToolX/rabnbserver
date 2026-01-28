package com.ra.rabnbserver.contract.support;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 合约地址配置
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "contract.address")
public class ContractAddressProperties {

    /**
     * PaymentUSDT 合约地址
     */
    @NotBlank(message = "contract.address.payment-usdt 不能为空")
    private String paymentUsdt;

    /**
     * CardNFT 合约地址
     */
    @NotBlank(message = "contract.address.card-nft 不能为空")
    private String cardNft;

    /**
     * AION 合约地址
     */
    @NotBlank(message = "contract.address.aion 不能为空")
    private String aion;
}
