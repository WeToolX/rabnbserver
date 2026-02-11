package com.ra.rabnbserver.contract;

import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.List;

import static com.ra.rabnbserver.contract.support.ContractTypeUtils.address;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.bytes32;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;

/**
 * CardNFT 合约调用接口（ERC1155 多 ID）
 *
 * 基础 ABI 数据来源（card_v2）：
 * - 卡牌ID：COPPER_ID / SILVER_ID / GOLD_ID
 * - 查询余额：balanceOf(user, id)
 * - 查询销毁累计：burnedAmount(user, id)
 * - 查询供应量：totalSupply(id)
 * - 是否授权：isApprovedForAll(user, admin)
 * - 分发卡牌：distribute(to, id, amount)
 * - 管理员代烧：burnWithOrder(from, id, amount, orderId)
 * - 查询订单：getOrder(orderId)
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
     * 订单返回结构
     */
    @Data
    public static class OrderInfo {
        /**
         * 用户地址
         */
        private String user;
        /**
         * 卡牌ID
         */
        private BigInteger cardId;
        /**
         * 数量
         */
        private BigInteger amount;
        /**
         * 时间戳（秒）
         */
        private BigInteger timestamp;
    }

    /**
     * 查询铜卡 ID
     *
     * @return 铜卡 ID
     */
    public BigInteger copperId() throws Exception {
        Function function = buildViewFunction("COPPER_ID", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询银卡 ID
     *
     * @return 银卡 ID
     */
    public BigInteger silverId() throws Exception {
        Function function = buildViewFunction("SILVER_ID", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询金卡 ID
     *
     * @return 金卡 ID
     */
    public BigInteger goldId() throws Exception {
        Function function = buildViewFunction("GOLD_ID", List.of(new TypeReference<Uint256>() {}));
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
     * 查询指定卡牌余额
     *
     * @param user   用户地址
     * @param cardId 卡牌ID
     * @return 余额
     */
    public BigInteger balanceOf(String user, BigInteger cardId) throws Exception {
        Function function = new Function(
                "balanceOf",
                List.of(address(user), uint256(cardId)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询指定卡牌销毁累计
     *
     * @param user   用户地址
     * @param cardId 卡牌ID
     * @return 累计销毁数量
     */
    public BigInteger burnedAmount(String user, BigInteger cardId) throws Exception {
        Function function = new Function(
                "burnedAmount",
                List.of(address(user), uint256(cardId)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询指定卡牌供应量
     *
     * @param cardId 卡牌ID
     * @return 当前供应量
     */
    public BigInteger totalSupply(BigInteger cardId) throws Exception {
        Function function = new Function(
                "totalSupply",
                List.of(uint256(cardId)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询订单是否已使用
     *
     * @param orderId 业务订单号（字符串或 bytes32 十六进制）
     * @return 是否已使用
     */
    public Boolean isOrderUsed(String orderId) throws Exception {
        Bytes32 orderHash = toOrderBytes(orderId);
        Function function = new Function(
                "orderUsed",
                List.of(orderHash),
                List.of(new TypeReference<Bool>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (Boolean) outputs.get(0).getValue();
    }

    /**
     * 查询订单详情
     *
     * @param orderId 业务订单号（字符串或 bytes32 十六进制）
     * @return 订单详情
     */
    public OrderInfo getOrder(String orderId) throws Exception {
        Bytes32 orderHash = toOrderBytes(orderId);
        Function function = new Function(
                "getOrder",
                List.of(orderHash),
                List.of(
                        new TypeReference<org.web3j.abi.datatypes.Address>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {}
                )
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        OrderInfo info = new OrderInfo();
        info.setUser(outputs.get(0).getValue().toString());
        info.setCardId((BigInteger) outputs.get(1).getValue());
        info.setAmount((BigInteger) outputs.get(2).getValue());
        info.setTimestamp((BigInteger) outputs.get(3).getValue());
        return info;
    }

    /**
     * 查询是否已授权管理员
     *
     * @param user  用户地址
     * @param admin 管理员地址（通常为当前签名地址）
     * @return 是否已授权
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
     * 查询卡牌 URI
     *
     * @param cardId 卡牌ID
     * @return URI
     */
    public String uri(BigInteger cardId) throws Exception {
        Function function = new Function(
                "uri",
                List.of(uint256(cardId)),
                List.of(new TypeReference<Utf8String>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0).getValue().toString();
    }

    /**
     * 分发 NFT（管理员）
     *
     * @param to     接收地址
     * @param cardId 卡牌ID
     * @param amount 数量
     * @return 交易回执
     */
    public TransactionReceipt distribute(String to, BigInteger cardId, BigInteger amount) throws Exception {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("发放数量必须大于 0");
        }
        Function function = new Function(
                "distribute",
                List.of(address(to), uint256(cardId), uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 管理员代用户销毁卡牌（带订单号）
     *
     * @param from   用户地址
     * @param cardId 卡牌ID
     * @param amount 数量
     * @param orderId 业务订单号（字符串或 bytes32 十六进制）
     * @return 交易回执
     */
    public TransactionReceipt burnWithOrder(String from, BigInteger cardId, BigInteger amount, String orderId) throws Exception {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("销毁数量必须大于 0");
        }
        Bytes32 orderHash = toOrderBytes(orderId);
        Function function = new Function(
                "burnWithOrder",
                List.of(address(from), uint256(cardId), uint256(amount), orderHash),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置管理员（setAdmin）
     *
     * @param admin   管理员地址
     * @param enabled 是否启用
     * @return 交易回执
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
     * 获取合约地址
     *
     * @return 合约地址
     */
    public String getAddress() {
        return contractAddressProperties.getCardNft();
    }

    /**
     * 获取当前签名地址（管理员钱包）
     *
     * @return 管理员地址
     */
    public String getAdminAddress() {
        return transactionManager.getFromAddress();
    }

    /**
     * 订单号转 bytes32（使用 keccak256）
     * 说明：推荐传入业务可读字符串，合约调用时统一转为 bytes32
     *
     * @param orderId 业务订单号
     * @return bytes32
     */
    private Bytes32 toOrderBytes(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("订单号不能为空");
        }
        String trimmed = orderId.trim();
        // 关键变更：支持两种输入方式
        // 1) 直接传 bytes32 十六进制（0x+64位） -> 原样使用
        // 2) 普通字符串订单号 -> 先做 keccak256 再转 bytes32
        if (trimmed.startsWith("0x") && trimmed.length() == 66) {
            return bytes32(trimmed);
        }
        String hash = Hash.sha3String(trimmed);
        return bytes32(hash);
    }
}
