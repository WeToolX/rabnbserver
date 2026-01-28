package com.ra.rabnbserver.contract;

import lombok.extern.slf4j.Slf4j;
import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
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
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.bytes32;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;

/**
 * PaymentUSDT 合约管理员调用接口
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Service
public class PaymentUsdtContract extends ContractBase {

    private final ContractAddressProperties contractAddressProperties;

    public PaymentUsdtContract(Web3j web3j, TransactionManager transactionManager, BlockchainProperties blockchainProperties,
                               ContractAddressProperties contractAddressProperties) {
        super(web3j, transactionManager, blockchainProperties);
        this.contractAddressProperties = contractAddressProperties;
    }

    /**
     * 扣款（仅 executor 可调用）
     *
     * @param orderIdHex 订单 ID（bytes32 十六进制）
     * @param user 用户地址
     * @param amount 扣款金额（最小单位）
     * @return 交易回执
     */
    public TransactionReceipt deposit(String orderIdHex, String user, BigInteger amount) throws Exception {
        Function function = new Function(
                "deposit",
                List.of(bytes32(orderIdHex), address(user), uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置收款地址（仅 admin）
     *
     * @param newTreasury 新收款地址
     * @return 交易回执
     */
    public TransactionReceipt setTreasury(String newTreasury) throws Exception {
        Function function = new Function(
                "setTreasury",
                List.of(address(newTreasury)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置执行者（仅 admin）
     *
     * @param newExecutor 新执行者地址
     * @return 交易回执
     */
    public TransactionReceipt setExecutor(String newExecutor) throws Exception {
        Function function = new Function(
                "setExecutor",
                List.of(address(newExecutor)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置管理员（仅 admin）
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
     * 暂停合约（仅 admin）
     *
     * @return 交易回执
     */
    public TransactionReceipt pause() throws Exception {
        Function function = new Function("pause", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 解除暂停（仅 admin）
     *
     * @return 交易回执
     */
    public TransactionReceipt unpause() throws Exception {
        Function function = new Function("unpause", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 查询订单是否已执行
     *
     * @param orderIdHex 订单 ID（bytes32 十六进制）
     * @return 是否已执行
     */
    public Boolean executed(String orderIdHex) throws Exception {
        Function function = new Function(
                "executed",
                List.of(bytes32(orderIdHex)),
                List.of(new TypeReference<Bool>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (Boolean) outputs.get(0).getValue();
    }

    /**
     * 查询 USDT 合约地址（动态读取链上）
     *
     * @return USDT 合约地址
     */
    public String usdtAddress() throws Exception {
        Function function = new Function(
                "USDT",
                List.of(),
                List.of(new TypeReference<Address>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0).getValue().toString();
    }

    /**
     * 查询授权额度（owner -> spender）
     *
     * @param owner 授权方地址
     * @param spender 被授权方地址
     * @return 授权额度
     */
    public BigInteger allowance(String owner, String spender) throws Exception {
        String usdt = usdtAddress();
        if (usdt == null || usdt.isBlank()) {
            return null;
        }
        Function function = new Function(
                "allowance",
                List.of(address(owner), address(spender)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(usdt, function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询授权额度（owner -> 本合约）
     *
     * @param owner 授权方地址
     * @return 授权额度
     */
    public BigInteger allowanceToPaymentUsdt(String owner) throws Exception {
        return allowance(owner, getAddress());
    }

    /**
     * 查询最小金额
     *
     * @return 最小金额
     */
    public BigInteger minAmount() throws Exception {
        Function function = buildViewFunction("MIN_AMOUNT", List.of(new TypeReference<Uint256>() {}));
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
        return contractAddressProperties.getPaymentUsdt();
    }
}
