package com.ra.rabnbserver.contract.support;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import java.math.BigInteger;

/**
 * 区块链基础配置
 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {

    /**
     * RPC 地址（主网）
     */
    @NotBlank(message = "blockchain.rpc-url 不能为空")
    private String rpcUrl;

    /**
     * 链 ID（主网/测试网）
     */
    @NotNull(message = "blockchain.chain-id 不能为空")
    private Long chainId;

    /**
     * 默认 Gas 限制（当估算失败时使用）
     */
    @NotNull(message = "blockchain.gas-limit-default 不能为空")
    @Min(value = 21000, message = "blockchain.gas-limit-default 不能小于 21000")
    private BigInteger gasLimitDefault = BigInteger.valueOf(1_000_000L);

    /**
     * 交易回执轮询间隔（毫秒）
     */
    @NotNull(message = "blockchain.tx-poll-interval-ms 不能为空")
    private Long txPollIntervalMs = 2000L;

    /**
     * 交易回执轮询超时时间（毫秒）
     */
    @NotNull(message = "blockchain.tx-timeout-ms 不能为空")
    private Long txTimeoutMs = 20L * 60L * 1000L;
}
