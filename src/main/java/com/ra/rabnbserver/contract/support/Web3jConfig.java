package com.ra.rabnbserver.contract.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.TransactionManager;

/**
 * Web3j 配置与管理员账户初始化
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Configuration
@RequiredArgsConstructor
public class Web3jConfig {

    private final BlockchainProperties blockchainProperties;
    private final ContractAdminProperties contractAdminProperties;
    private final PrivateKeyCryptoService privateKeyCryptoService;

    /**
     * Web3j 客户端
     *
     * @return Web3j 实例
     */
    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(blockchainProperties.getRpcUrl()));
    }

    /**
     * 管理员账户凭证
     *
     * @return Credentials
     */
    @Bean
    public Credentials adminCredentials() {
        String privateKey = privateKeyCryptoService.decryptPrivateKey(contractAdminProperties.getPrivateKeyEnc());
        return Credentials.create(privateKey);
    }

    /**
     * 交易管理器（支持链 ID）
     *
     * @param web3j Web3j 客户端
     * @param credentials 管理员账户
     * @return 交易管理器
     */
    @Bean
    public TransactionManager transactionManager(Web3j web3j, Credentials credentials) {
        long chainId = blockchainProperties.getChainId();
        return new RawTransactionManager(web3j, credentials, chainId);
    }
}
