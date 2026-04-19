package com.ra.rabnbserver.contract;

import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.List;

import static com.ra.rabnbserver.contract.support.ContractTypeUtils.address;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.bytes32;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;

/**
 * AIR 收款合约封装
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Service
public class AirPaymentCollectorContract extends ContractBase {

    private final ContractAddressProperties contractAddressProperties;

    public AirPaymentCollectorContract(Web3j web3j,
                                       TransactionManager transactionManager,
                                       BlockchainProperties blockchainProperties,
                                       ContractAddressProperties contractAddressProperties) {
        super(web3j, transactionManager, blockchainProperties);
        this.contractAddressProperties = contractAddressProperties;
    }

    /**
     * 获取 AIR 收款合约地址
     *
     * @return 合约地址
     */
    public String getAddress() {
        return contractAddressProperties.getAirPaymentCollector();
    }

    /**
     * 获取当前链上执行地址
     *
     * @return 执行地址
     */
    public String getOperatorAddress() {
        return transactionManager.getFromAddress();
    }

    /**
     * 执行 AIR 扣款
     *
     * @param orderIdHex 订单号（bytes32 hex）
     * @param payer      付款用户地址
     * @param amount     扣款数量（18 位最小单位）
     * @return 交易回执
     */
    public TransactionReceipt collect(String orderIdHex, String payer, BigInteger amount) throws Exception {
        Function function = new Function(
                "collect",
                List.of(bytes32(orderIdHex), address(payer), uint256(amount)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 预检查 AIR 扣款条件
     *
     * @param orderIdHex 订单号（bytes32 hex）
     * @param payer      付款用户地址
     * @param amount     扣款数量（18 位最小单位）
     * @return 预检查结果
     */
    public PreviewCollectResult previewCollect(String orderIdHex, String payer, BigInteger amount) throws Exception {
        Function function = new Function(
                "previewCollect",
                List.of(bytes32(orderIdHex), address(payer), uint256(amount)),
                List.of(
                        new TypeReference<Bool>() {},
                        new TypeReference<Uint8>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Address>() {}
                )
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.size() < 5) {
            throw new IllegalStateException("AIR 收款合约预检查返回值数量异常");
        }
        PreviewCollectResult result = new PreviewCollectResult();
        result.setExecutable((Boolean) outputs.get(0).getValue());
        BigInteger codeValue = (BigInteger) outputs.get(1).getValue();
        result.setCode(codeValue == null ? 0 : codeValue.intValue());
        result.setBalance((BigInteger) outputs.get(2).getValue());
        result.setAllowance((BigInteger) outputs.get(3).getValue());
        result.setRecipient(outputs.get(4).getValue().toString());
        result.setCodeMessage(AirCollectorErrorCode.getDescriptionByCode(result.getCode()));
        return result;
    }

    /**
     * 查询收款地址
     *
     * @return 收款地址
     */
    public String treasury() throws Exception {
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
     * AIR 收款合约预检查结果
     */
    @Data
    public static class PreviewCollectResult {
        /**
         * 是否可执行
         */
        private Boolean executable;
        /**
         * 错误码
         */
        private Integer code;
        /**
         * 用户 AIR 余额
         */
        private BigInteger balance;
        /**
         * 用户 AIR 授权额度
         */
        private BigInteger allowance;
        /**
         * 当前收款地址
         */
        private String recipient;
        /**
         * 错误码说明
         */
        private String codeMessage;
    }

    /**
     * AIR 收款合约错误码定义
     */
    @Getter
    private enum AirCollectorErrorCode {
        NOT_OWNER(1, "调用者不是 owner"),
        NOT_COLLECTOR(2, "调用者不是授权收款员"),
        CONTRACT_PAUSED(3, "收款合约已暂停"),
        INVALID_TREASURY(4, "收款地址未配置"),
        INVALID_COLLECTOR(5, "收款员地址非法"),
        INVALID_NEW_OWNER(6, "新 owner 地址非法"),
        INVALID_PAYER(7, "付款用户地址非法"),
        ZERO_AMOUNT(8, "闪兑数量必须大于 0"),
        EMPTY_ORDER_ID(9, "订单号不能为空"),
        ORDER_ALREADY_USED(10, "订单号已被使用"),
        INSUFFICIENT_ALLOWANCE(11, "AIR 授权额度不足"),
        INSUFFICIENT_BALANCE(12, "AIR 余额不足"),
        TOKEN_TRANSFER_FAILED(13, "AIR 扣款失败"),
        ORDER_NOT_FOUND(14, "订单不存在");

        private final int code;
        private final String description;

        AirCollectorErrorCode(int code, String description) {
            this.code = code;
            this.description = description;
        }

        public static String getDescriptionByCode(Integer code) {
            if (code == null) {
                return "未知错误";
            }
            for (AirCollectorErrorCode value : values()) {
                if (value.code == code) {
                    return value.description;
                }
            }
            return "未知错误码:" + code;
        }
    }
}
