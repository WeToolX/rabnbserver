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
     *         返回类型：TransactionReceipt（Java 对象）
     *         JSON 序列化示例（字段可能因节点实现略有差异）：
     *         {
     *           "status": "0x1",
     *           "transactionHash": "0x...",
     *           "from": "0x...",
     *           "to": "0x...",
     *           "blockNumber": "0x...",
     *           "gasUsed": "0x...",
     *           "effectiveGasPrice": "0x...",
     *           "logs": [
     *             { "address": "0x...", "topics": ["0x..."], "data": "0x..." }
     *           ],
     *           "revertReason": null
     *         }
     *         字段含义：
     *         - status：0x1 成功，0x0 失败（回退）
     *         - transactionHash：交易哈希
     *         - from/to：发起地址/合约地址
     *         - blockNumber：打包区块号（16 进制字符串）
     *         - gasUsed/effectiveGasPrice：本次交易 gas 消耗/实际 gas 单价
     *         - logs：事件列表（可能包含 USDT Transfer 与 PaymentUSDT Deposit）
     *         - revertReason：失败原因（可能为空）
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
     *         返回类型：TransactionReceipt（Java 对象）
     *         JSON 序列化示例：
     *         {
     *           "status": "0x1",
     *           "transactionHash": "0x...",
     *           "from": "0x...",
     *           "to": "0x...",
     *           "blockNumber": "0x...",
     *           "gasUsed": "0x...",
     *           "effectiveGasPrice": "0x...",
     *           "logs": [],
     *           "revertReason": null
     *         }
     *         字段含义同上：status=0x1 成功，status=0x0 失败（回退）
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
     *         返回类型：TransactionReceipt（Java 对象）
     *         JSON 序列化示例与字段含义同上：status=0x1 成功，status=0x0 失败（回退）
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
     *         返回类型：TransactionReceipt（Java 对象）
     *         JSON 序列化示例与字段含义同上：status=0x1 成功，status=0x0 失败（回退）
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
     *         返回类型：TransactionReceipt（Java 对象）
     *         JSON 序列化示例与字段含义同上：status=0x1 成功，status=0x0 失败（回退）
     */
    public TransactionReceipt pause() throws Exception {
        Function function = new Function("pause", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 解除暂停（仅 admin）
     *
     * @return 交易回执
     *         返回类型：TransactionReceipt（Java 对象）
     *         JSON 序列化示例与字段含义同上：status=0x1 成功，status=0x0 失败（回退）
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
     *         返回类型：Boolean
     *         JSON 序列化示例：true / false
     *         含义：true 支付成功，false 支付失败或者订单不存在，可能为 null（RPC 未返回）
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
     *         返回类型：String
     *         JSON 序列化示例："0x..."
     *         含义：USDT 合约地址，可能为 null（RPC 未返回）
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
     * 查询管理员地址
     *
     * @return 管理员地址
     *         返回类型：String
     *         JSON 序列化示例："0x..."
     *         含义：管理员地址，可能为 null（RPC 未返回）
     */
    public String adminAddress() throws Exception {
        Function function = new Function(
                "admin",
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
     * 查询执行者地址
     *
     * @return 执行者地址
     *         返回类型：String
     *         JSON 序列化示例："0x..."
     *         含义：执行者地址，可能为 null（RPC 未返回）
     */
    public String executorAddress() throws Exception {
        Function function = new Function(
                "executor",
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
     * 查询收款地址
     *
     * @return 收款地址
     *         返回类型：String
     *         JSON 序列化示例："0x..."
     *         含义：收款地址，可能为 null（RPC 未返回）
     */
    public String treasuryAddress() throws Exception {
        Function function = new Function(
                "treasury",
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
     *         返回类型：BigInteger
     *         JSON 序列化示例：123456789
     *         含义：授权额度（链上原始最小单位），可能为 null（RPC 未返回）
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
     *         返回类型：BigInteger
     *         JSON 序列化示例：123456789
     *         含义：授权额度（链上原始最小单位），可能为 null（RPC 未返回）
     */
    public BigInteger allowanceToPaymentUsdt(String owner) throws Exception {
        return allowance(owner, getAddress());
    }

    /**
     * 查询用户 USDT 余额
     *
     * @param owner 用户地址
     * @return 余额
     *         返回类型：BigInteger
     *         JSON 序列化示例：123456789
     *         含义：余额（链上原始最小单位），可能为 null（RPC 未返回）
     */
    public BigInteger balanceOf(String owner) throws Exception {
        String usdt = usdtAddress();
        if (usdt == null || usdt.isBlank()) {
            return null;
        }
        Function function = new Function(
                "balanceOf",
                List.of(address(owner)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(usdt, function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询最小金额
     *
     * @return 最小金额
     *         返回类型：BigInteger
     *         JSON 序列化示例：1000000000000000000
     *         含义：最小扣款金额（链上原始最小单位），可能为 null（RPC 未返回）
     */
    public BigInteger minAmount() throws Exception {
        Function function = buildViewFunction("minAmount", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 设置最小扣款金额
     *
     * @param amount 最小扣款金额（链上原始最小单位）
     * @return 交易回执
     *         返回类型：TransactionReceipt（Java 对象）
     *         status=0x1 成功，status=0x0 失败（回退）
     */
    public TransactionReceipt setMinAmount(BigInteger amount) throws Exception {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("最小扣款金额必须大于 0");
        }
        Function function = new Function(
                "setMinAmount",
                List.of(uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
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
