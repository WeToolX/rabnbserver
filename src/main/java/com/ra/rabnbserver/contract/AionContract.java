package com.ra.rabnbserver.contract;

import lombok.extern.slf4j.Slf4j;
import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.List;

import static com.ra.rabnbserver.contract.support.ContractTypeUtils.address;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint8;

/**
 * AION 合约管理员调用接口
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Service
public class AionContract extends ContractBase {

    private final ContractAddressProperties contractAddressProperties;

    public AionContract(Web3j web3j, TransactionManager transactionManager, BlockchainProperties blockchainProperties,
                        ContractAddressProperties contractAddressProperties) {
        super(web3j, transactionManager, blockchainProperties);
        this.contractAddressProperties = contractAddressProperties;
    }

    /**
     * 管理员发放代币（锁仓计划）
     *
     * @param to 接收地址
     * @param amount 发放数量
     * @param plan 锁仓计划（0=ONE_MONTH, 1=TWO_MONTHS, 2=FOUR_MONTHS）
     * @return 交易回执
     */
    public TransactionReceipt faucetMint(String to, BigInteger amount, int plan) throws Exception {
        Function function = new Function(
                "faucetMint",
                List.of(address(to), uint256(amount), uint8(plan)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置管理员
     *
     * @param newAdmin 新管理员地址
     * @return 交易回执
     */
    public TransactionReceipt setAdmin(String newAdmin) throws Exception {
        Function function = new Function(
                "setAdmin",
                List.of(address(newAdmin)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 撤销管理员
     *
     * @param admin 管理员地址
     * @return 交易回执
     */
    public TransactionReceipt revokeAdmin(String admin) throws Exception {
        Function function = new Function(
                "revokeAdmin",
                List.of(address(admin)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 暂停合约
     *
     * @return 交易回执
     */
    public TransactionReceipt pause() throws Exception {
        Function function = new Function("pause", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 解除暂停
     *
     * @return 交易回执
     */
    public TransactionReceipt unpause() throws Exception {
        Function function = new Function("unpause", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置社区地址
     *
     * @param newCommunity 新社区地址
     * @return 交易回执
     */
    public TransactionReceipt setCommunity(String newCommunity) throws Exception {
        Function function = new Function(
                "setCommunity",
                List.of(address(newCommunity)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置兑换参数
     *
     * @param fixedEnabled 是否启用固定价格
     * @param fixedAmount 固定价格金额
     * @param burnBps 销毁比例（bps）
     * @param communityBps 社区比例（bps）
     * @return 交易回执
     */
    public TransactionReceipt setExchangeParams(
            boolean fixedEnabled,
            BigInteger fixedAmount,
            BigInteger burnBps,
            BigInteger communityBps
    ) throws Exception {
        Function function = new Function(
                "setExchangeParams",
                List.of(
                        new org.web3j.abi.datatypes.Bool(fixedEnabled),
                        uint256(fixedAmount),
                        uint256(burnBps),
                        uint256(communityBps)
                ),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 查询是否暂停
     *
     * @return 是否暂停
     */
    public Boolean paused() throws Exception {
        Function function = buildViewFunction("paused", List.of(new TypeReference<Bool>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (Boolean) outputs.get(0).getValue();
    }

    /**
     * 查询固定价格开关
     *
     * @return 是否启用固定价格
     */
    public Boolean fixedPriceEnabled() throws Exception {
        Function function = buildViewFunction("fixedPriceEnabled", List.of(new TypeReference<Bool>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (Boolean) outputs.get(0).getValue();
    }

    /**
     * 查询固定价格金额
     *
     * @return 固定价格
     */
    public BigInteger fixedPriceAmount() throws Exception {
        Function function = buildViewFunction("fixedPriceAmount", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询销毁比例
     *
     * @return 销毁比例
     */
    public BigInteger burnBps() throws Exception {
        Function function = buildViewFunction("burnBps", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询社区比例
     *
     * @return 社区比例
     */
    public BigInteger communityBps() throws Exception {
        Function function = buildViewFunction("communityBps", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 获取合约地址
     *
     * @return 合约地址
     */
    public String getAddress() {
        return contractAddressProperties.getAion();
    }
}
