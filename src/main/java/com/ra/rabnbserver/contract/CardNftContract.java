package com.ra.rabnbserver.contract;

import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.List;

import static com.ra.rabnbserver.contract.support.ContractTypeUtils.address;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;

/**
 * CardNFT 合约调用接口（ERC1155 单 ID）
 *
 * 基础 ABI 数据来源：
 * - 历史已分发数量：totalMinted()
 * - 当前仍存在数量：totalSupply()
 * - 该地址历史销毁：burnedAmount(user)
 * - 剩余未分发：remainingMintable()
 * - 历史硬上限：MAX_SUPPLY
 * - 是否已授权：isApprovedForAll(user, admin)
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Service
public class CardNftContract extends ContractBase {

    private final ContractAddressProperties contractAddressProperties;

    public CardNftContract(Web3j web3j, TransactionManager transactionManager, BlockchainProperties blockchainProperties,
                           ContractAddressProperties contractAddressProperties) {
        super(web3j, transactionManager, blockchainProperties);
        this.contractAddressProperties = contractAddressProperties;
    }

    /**
     * 查询卡牌 ID（固定为 1）
     *
     * @return 卡牌 ID
     *         返回类型：BigInteger
     *         JSON 序列化示例：1
     */
    public BigInteger cardId() throws Exception {
        Function function = buildViewFunction("CARD_ID", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询最大供应量
     *
     * @return 最大供应量
     *         返回类型：BigInteger
     */
    public BigInteger maxSupply() throws Exception {
        Function function = buildViewFunction("MAX_SUPPLY", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询名称
     *
     * @return 名称
     *         返回类型：String
     */
    public String name() throws Exception {
        Function function = buildViewFunction("name", List.of(new TypeReference<Utf8String>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0).getValue().toString();
    }

    /**
     * 查询符号
     *
     * @return 符号
     *         返回类型：String
     */
    public String symbol() throws Exception {
        Function function = buildViewFunction("symbol", List.of(new TypeReference<Utf8String>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0).getValue().toString();
    }

    /**
     * 查询用户余额（balanceOf(user, 1)）
     *
     * @param user 用户地址
     * @return 余额
     *         返回类型：BigInteger
     */
    public BigInteger balanceOf(String user) throws Exception {
        BigInteger id = cardId();
        if (id == null) {
            return null;
        }
        Function function = new Function(
                "balanceOf",
                List.of(address(user), uint256(id)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询用户累计销毁数量
     *
     * @param user 用户地址
     * @return 累计销毁数量
     *         返回类型：BigInteger
     */
    public BigInteger burnedAmount(String user) throws Exception {
        Function function = new Function(
                "burnedAmount",
                List.of(address(user)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询历史已分发数量（totalMinted）
     *
     * @return 历史已分发数量
     *         返回类型：BigInteger
     */
    public BigInteger totalMinted() throws Exception {
        Function function = buildViewFunction("totalMinted", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询当前仍存在数量（totalSupply）
     *
     * @return 当前仍存在数量
     *         返回类型：BigInteger
     */
    public BigInteger totalSupply() throws Exception {
        Function function = buildViewFunction("totalSupply", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询剩余未分发数量（remainingMintable）
     *
     * @return 剩余未分发数量
     *         返回类型：BigInteger
     */
    public BigInteger remainingMintable() throws Exception {
        Function function = buildViewFunction("remainingMintable", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询是否已授权管理员
     *
     * @param user 用户地址
     * @param admin 管理员地址（通常为当前签名地址）
     * @return 是否已授权
     *         返回类型：Boolean
     */
    public Boolean isApprovedForAll(String user, String admin) throws Exception {
        Function function = new Function(
                "isApprovedForAll",
                List.of(address(user), address(admin)),
                List.of(new TypeReference<Bool>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (Boolean) outputs.get(0).getValue();
    }

    /**
     * 分发 NFT（distribute）
     *
     * @param to 接收地址
     * @param amount 数量
     * @implNote 后端调用流程：
     *           1) 校验 amount > 0
     *           2) 查询剩余可分发数量 remainingMintable()
     *           3) 校验 amount ≤ remainingMintable()
     *           4) 调用合约 distribute(to, amount)
     *           5) 等待交易确认
     * @return 交易回执
     *         返回类型：TransactionReceipt（Java 对象）
     *         status=0x1 成功，status=0x0 失败（回退）
     */
    public TransactionReceipt distribute(String to, BigInteger amount) throws Exception {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("发放数量必须大于 0");
        }
        BigInteger remaining = remainingMintable();
        if (remaining == null) {
            throw new IllegalStateException("无法获取可分发额度");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("发放数量超出可分发额度");
        }
        Function function = new Function(
                "distribute",
                List.of(address(to), uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 销毁用户卡牌（标准流程）
     *
     * @param user 用户地址
     * @param amount 销毁数量
     * @implNote 后端销毁流程：
     *           1) 校验 amount > 0
     *           2) 查询用户是否授权管理员 isApprovedForAll(user, admin)
     *           3) 查询用户余额 balanceOf(user, 1)
     *           4) 校验余额 ≥ 销毁数量
     *           5) 调用合约 burn(user, 1, amount)
     *           6) 等待交易确认
     *           7) 可选：读取 burnedAmount(user) 作为业务回执
     * @return 交易回执
     *         返回类型：TransactionReceipt（Java 对象）
     *         status=0x1 成功，status=0x0 失败（回退）
     */
    public TransactionReceipt burnUser(String user, BigInteger amount) throws Exception {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("销毁数量必须大于 0");
        }
        String admin = transactionManager.getFromAddress();
        Boolean approved = isApprovedForAll(user, admin);
        if (approved == null || !approved) {
            throw new IllegalStateException("用户未授权管理员");
        }
        BigInteger balance = balanceOf(user);
        if (balance == null || balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("用户余额不足");
        }
        BigInteger id = cardId();
        if (id == null) {
            throw new IllegalStateException("无法获取卡牌 ID");
        }
        Function function = new Function(
                "burn",
                List.of(address(user), uint256(id), uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置管理员（setAdmin）
     *
     * @param admin 管理员地址
     * @param enabled 是否启用
     * @return 交易回执
     *         返回类型：TransactionReceipt（Java 对象）
     */
    public TransactionReceipt setAdmin(String admin, boolean enabled) throws Exception {
        Function function = new Function(
                "setAdmin",
                List.of(address(admin), new Bool(enabled)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置 URI
     *
     * @param newUri 新 URI
     * @return 交易回执
     *         返回类型：TransactionReceipt（Java 对象）
     */
    public TransactionReceipt setUri(String newUri) throws Exception {
        Function function = new Function(
                "setURI",
                List.of(new Utf8String(newUri)),
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
        return contractAddressProperties.getCardNft();
    }
}
