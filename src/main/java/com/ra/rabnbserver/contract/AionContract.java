package com.ra.rabnbserver.contract;

import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.exception.AionContractException;
import com.ra.rabnbserver.exception.ChainCallException;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Hash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ra.rabnbserver.contract.support.ContractTypeUtils.address;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint8;

/**
 * AION 合约管理员调用接口（新版 AiRWord）
 * 说明：
 * - 锁仓周期在测试合约中已调整为分钟级（1/2/4 分钟），lockType 仍为 1/2/3。
 * - 读写接口均基于 air.md ABI 定义实现。
 * @author qiexi
 */
@Slf4j(topic = "com.ra.rabnbserver.service.contract")
@Service
public class AionContract extends ContractBase {

    /**
     * BizError 错误码签名
     */
    private static final String BIZ_ERROR_SIGNATURE = "BizError(uint8)";

    /**
     * BizError 选择器（前 4 字节）
     */
    private static final String BIZ_ERROR_SELECTOR = Hash.sha3String(BIZ_ERROR_SIGNATURE).substring(0, 10);

    /**
     * Error(string) 选择器（前 4 字节）
     */
    private static final String ERROR_STRING_SELECTOR = "0x08c379a0";

    /**
     * Hex 数据提取正则
     */
    private static final Pattern HEX_PATTERN = Pattern.compile("0x[0-9a-fA-F]+");

    private final ContractAddressProperties contractAddressProperties;

    public AionContract(Web3j web3j, TransactionManager transactionManager, BlockchainProperties blockchainProperties,
                        ContractAddressProperties contractAddressProperties) {
        super(web3j, transactionManager, blockchainProperties);
        this.contractAddressProperties = contractAddressProperties;
    }

    /**
     * 获取合约地址
     *
     * @return 合约地址
     */
    public String getAddress() {
        return contractAddressProperties.getAion();
    }

    /**
     * AION 合约 BizError 错误码定义
     */
    @Getter
    private enum AionBizErrorCode {
        /**
         * 非管理员调用
         */
        NOT_ADMIN(1, "NOT_ADMIN", "非管理员调用"),
        /**
         * 挖矿未启动
         */
        MINING_NOT_STARTED(2, "MINING_NOT_STARTED", "挖矿未启动"),
        /**
         * 仓位参数非法（非 1/2/3）
         */
        INVALID_LOCK_TYPE(3, "INVALID_LOCK_TYPE", "仓位参数非法（非 1/2/3）"),
        /**
         * 订单号重复
         */
        ORDER_ID_DUPLICATE(4, "ORDER_ID_DUPLICATE", "订单号重复"),
        /**
         * 年度额度不足
         */
        ANNUAL_BUDGET_EXCEEDED(5, "ANNUAL_BUDGET_EXCEEDED", "年度额度不足"),
        /**
         * 可兑换数量不足 target
         */
        EXCHANGE_TARGET_NOT_MET(6, "EXCHANGE_TARGET_NOT_MET", "可兑换数量不足 target"),
        /**
         * 本次无可领取数量
         */
        NO_CLAIMABLE(7, "NO_CLAIMABLE", "本次无可领取数量"),
        /**
         * 订单不存在
         */
        ORDER_NOT_FOUND(8, "ORDER_NOT_FOUND", "订单不存在"),
        /**
         * 未获得用户授权
         */
        NOT_AUTHORIZED(9, "NOT_AUTHORIZED", "未获得用户授权"),
        /**
         * 分发类型非法（非 1/2）
         */
        INVALID_DIST_TYPE(10, "INVALID_DIST_TYPE", "分发类型非法（非 1/2）"),
        /**
         * 预估参数非法（如 0）
         */
        INVALID_GAS_PARAM(11, "INVALID_GAS_PARAM", "预估参数非法（如 0）"),
        /**
         * 数量为 0
         */
        ZERO_AMOUNT(12, "ZERO_AMOUNT", "数量为 0"),
        /**
         * 地址非法（零地址）
         */
        INVALID_ADDRESS(13, "INVALID_ADDRESS", "地址非法（零地址）"),
        /**
         * 超出总量上限
         */
        CAP_EXCEEDED(14, "CAP_EXCEEDED", "超出总量上限"),
        /**
         * 余额不足
         */
        INSUFFICIENT_BALANCE(15, "INSUFFICIENT_BALANCE", "余额不足"),
        /**
         * 授权额度不足
         */
        INSUFFICIENT_ALLOWANCE(16, "INSUFFICIENT_ALLOWANCE", "授权额度不足"),
        /**
         * 批量条数超过上限
         */
        BATCH_LIMIT_EXCEEDED(17, "BATCH_LIMIT_EXCEEDED", "批量条数超过上限"),
        /**
         * 批量参数为空
         */
        EMPTY_BATCH(18, "EMPTY_BATCH", "批量参数为空");

        /**
         * 错误码值（链上返回的 code）
         */
        private final int code;
        /**
         * 错误码名称（链上常量名）
         */
        private final String errorName;
        /**
         * 错误码含义说明（中文）
         */
        private final String description;

        AionBizErrorCode(int code, String errorName, String description) {
            this.code = code;
            this.errorName = errorName;
            this.description = description;
        }

        public static AionBizErrorCode fromCode(int code) {
            for (AionBizErrorCode value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
            return null;
        }
    }

    /**
     * 解析 BizError 错误码
     *
     * @param revertReason revert 原始信息
     * @return 错误码信息
     */
    private AionBizErrorCode parseBizError(String revertReason) {

        if (revertReason == null || revertReason.isBlank()) {
            return null;
        }
        String hex = extractHex(revertReason);
        if (hex == null) {
            return null;
        }
        String normalized = hex.toLowerCase();
        if (!normalized.startsWith(BIZ_ERROR_SELECTOR)) {
            return null;
        }
        String data = normalized.substring(BIZ_ERROR_SELECTOR.length());
        if (data.length() < 64) {
            return null;
        }
        String codeHex = data.substring(0, 64);
        BigInteger code = new BigInteger(codeHex, 16);
        return AionBizErrorCode.fromCode(code.intValue());
    }

    /**
     * 构造合约异常（包含原始与解码信息）
     *
     * @param revertReason 原始信息
     * @return 合约异常
     */
    private AionContractException buildContractException(Function function, String callData, String from, String revertReason) {
        if (revertReason == null || revertReason.isBlank()) {
            return new AionContractException("未知原因", getAddress(), function.getName(), from, callData, revertReason, null, null, null);
        }
        AionBizErrorCode code = parseBizError(revertReason);
        if (code != null) {
            return new AionContractException(
                    code.getDescription(),
                    getAddress(),
                    function.getName(),
                    from,
                    callData,
                    revertReason,
                    code.getCode(),
                    code.getErrorName(),
                    code.getDescription()
            );
        }
        String errorMessage = parseErrorString(revertReason);
        if (errorMessage != null && !errorMessage.isBlank()) {
            return new AionContractException(
                    errorMessage,
                    getAddress(),
                    function.getName(),
                    from,
                    callData,
                    revertReason,
                    null,
                    "Error(string)",
                    errorMessage
            );
        }
        if (revertReason != null && !revertReason.isBlank()) {
            return new AionContractException(
                    revertReason,
                    getAddress(),
                    function.getName(),
                    from,
                    callData,
                    revertReason,
                    null,
                    null,
                    revertReason
            );
        }
        return new AionContractException("未知原因", getAddress(), function.getName(), from, callData, revertReason, null, null, null);
    }

    /**
     * 构造链上异常（非 revert 类型）
     *
     * @param function 函数
     * @param callData 调用数据
     * @param from 发起地址
     * @param ex 原始异常
     * @return 链上异常
     */
    private ChainCallException buildChainException(Function function, String callData, String from, Exception ex) {
        String message = ex == null || ex.getMessage() == null ? "链上调用异常" : ex.getMessage();
        return new ChainCallException(message, getAddress(), function.getName(), from, callData, ex == null ? null : ex.getMessage(), ex);
    }

    /**
     * 解析 Error(string) 错误信息
     *
     * @param revertReason revert 原始信息
     * @return 解析后的错误信息
     */
    private String parseErrorString(String revertReason) {
        String hex = extractHex(revertReason);
        if (hex == null) {
            return null;
        }
        String normalized = hex.toLowerCase();
        if (!normalized.startsWith(ERROR_STRING_SELECTOR)) {
            return null;
        }
        String data = normalized.substring(ERROR_STRING_SELECTOR.length());
        if (data.isBlank()) {
            return null;
        }
        String payload = "0x" + data;
        try {
            @SuppressWarnings({"rawtypes", "unchecked"})
            List<TypeReference<Type>> outputTypes = (List) List.of(new TypeReference<Utf8String>() {});
            @SuppressWarnings({"rawtypes", "unchecked"})
            List<Type<?>> decoded = (List) FunctionReturnDecoder.decode(payload, outputTypes);
            if (decoded.isEmpty()) {
                return null;
            }
            return decoded.getFirst().getValue().toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 提取 Hex 字符串
     *
     * @param value 原始内容
     * @return Hex 字符串
     */
    private String extractHex(String value) {
        Matcher matcher = HEX_PATTERN.matcher(value);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 只读调用（解析 BizError）
     *
     * @param function 函数
     * @return 解码结果
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Type<?>> callViewFunction(Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        String from = transactionManager.getFromAddress();
        try {
            Transaction transaction = Transaction.createEthCallTransaction(from, getAddress(), data);
            EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
            if (response.isReverted()) {
                throw buildContractException(function, data, from, response.getRevertReason());
            }
            return (List) FunctionReturnDecoder.decode(response.getValue(), function.getOutputParameters());
        } catch (AionContractException ex) {
            throw ex;
        } catch (Exception ex) {
            throw buildChainException(function, data, from, ex);
        }
    }

    /**
     * 写操作调用（解析 BizError）
     *
     * @param function 函数
     * @return 交易回执
     */
    private TransactionReceipt sendAionTransaction(Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        String from = transactionManager.getFromAddress();
        try {
            TransactionReceipt receipt = sendTransaction(getAddress(), function);
            if (receipt == null) {
                return null;
            }
            if (isReceiptFailed(receipt)) {
                String reason = receipt.getRevertReason();
                if (reason == null || reason.isBlank()) {
                    reason = queryRevertReason(function);
                }
                throw buildContractException(function, data, from, reason);
            }
            return receipt;
        } catch (AionContractException ex) {
            throw ex;
        } catch (ChainCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw buildChainException(function, data, from, ex);
        }
    }

    /**
     * 判断交易回执是否失败
     *
     * @param receipt 回执
     * @return 是否失败
     */
    private boolean isReceiptFailed(TransactionReceipt receipt) {
        String status = receipt.getStatus();
        if (status == null || status.isBlank()) {
            return false;
        }
        try {
            String clean = status.startsWith("0x") ? status.substring(2) : status;
            return new BigInteger(clean, 16).equals(BigInteger.ZERO);
        } catch (NumberFormatException ex) {
            return "0x0".equalsIgnoreCase(status);
        }
    }

    /**
     * 通过 eth_call 获取 revert 信息
     *
     * @param function 函数
     * @return revert 原始信息
     */
    private String queryRevertReason(Function function) throws Exception {
        String data = FunctionEncoder.encode(function);
        String from = transactionManager.getFromAddress();
        Transaction transaction = Transaction.createEthCallTransaction(from, getAddress(), data);
        EthCall response = web3j.ethCall(transaction, DefaultBlockParameterName.LATEST).send();
        if (response.isReverted()) {
            return response.getRevertReason();
        }
        return null;
    }

    // ===================== ERC20 基础读取 =====================

    /**
     * 查询名称
     *
     * @return 名称
     *         返回类型：String
     */
    public String name() throws Exception {
        Function function = buildViewFunction("name", List.of(new TypeReference<Utf8String>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.getFirst().getValue().toString();
    }

    /**
     * 查询符号
     *
     * @return 符号
     *         返回类型：String
     */
    public String symbol() throws Exception {
        Function function = buildViewFunction("symbol", List.of(new TypeReference<Utf8String>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.getFirst().getValue().toString();
    }

    /**
     * 查询精度
     *
     * @return 精度
     *         返回类型：BigInteger
     */
    public BigInteger decimals() throws Exception {
        Function function = buildViewFunction("decimals", List.of(new TypeReference<Uint8>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询总供应量
     *
     * @return 总供应量
     *         返回类型：BigInteger
     */
    public BigInteger totalSupply() throws Exception {
        Function function = buildViewFunction("totalSupply", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询余额
     *
     * @param account 用户地址
     * @return 余额
     *         返回类型：BigInteger
     */
    public BigInteger balanceOf(String account) throws Exception {
        Function function = new Function(
                "balanceOf",
                List.of(address(account)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询授权额度
     *
     * @param owner 授权方
     * @param spender 被授权方
     * @return 授权额度
     *         返回类型：BigInteger
     */
    public BigInteger allowance(String owner, String spender) throws Exception {
        Function function = new Function(
                "allowance",
                List.of(address(owner), address(spender)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询合约部署者地址
     *
     * @return 部署者地址
     *         返回类型：String
     */
    public String owner() throws Exception {
        Function function = buildViewFunction("owner", List.of(new TypeReference<Address>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.getFirst().getValue().toString();
    }

    /**
     * 查询管理员地址
     *
     * @return 管理员地址
     *         返回类型：String
     */
    public String admin() throws Exception {
        Function function = buildViewFunction("admin", List.of(new TypeReference<Address>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.getFirst().getValue().toString();
    }

    /**
     * 查询社区地址
     *
     * @return 社区地址
     *         返回类型：String
     */
    public String community() throws Exception {
        Function function = buildViewFunction("community", List.of(new TypeReference<Address>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.getFirst().getValue().toString();
    }

    /**
     * 查询 CAP（最大总量）
     *
     * @return CAP
     *         返回类型：BigInteger
     */
    public BigInteger cap() throws Exception {
        Function function = buildViewFunction("CAP", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询挖矿开始时间
     *
     * @return 挖矿开始时间戳
     *         返回类型：BigInteger
     */
    public BigInteger miningStart() throws Exception {
        Function function = buildViewFunction("miningStart", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询已结算年份
     *
     * @return 已结算年份
     *         返回类型：BigInteger
     */
    public BigInteger lastSettledYear() throws Exception {
        Function function = buildViewFunction("lastSettledYear", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询当前年度预算
     *
     * @return 年度预算
     *         返回类型：BigInteger
     */
    public BigInteger yearBudget() throws Exception {
        Function function = buildViewFunction("yearBudget", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询当前年度已分发
     *
     * @return 已分发数量
     *         返回类型：BigInteger
     */
    public BigInteger yearMinted() throws Exception {
        Function function = buildViewFunction("yearMinted", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询剩余可挖额度
     *
     * @return 剩余可挖额度
     *         返回类型：BigInteger
     */
    public BigInteger remainingCap() throws Exception {
        Function function = buildViewFunction("remainingCap", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询年度开始时间
     *
     * @return 年度开始时间戳
     *         返回类型：BigInteger
     */
    public BigInteger yearStartTs() throws Exception {
        Function function = buildViewFunction("yearStartTs", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询扫描上限
     *
     * @return 扫描上限
     *         返回类型：BigInteger
     */
    public BigInteger getMaxScanLimit() throws Exception {
        Function function = buildViewFunction("getMaxScanLimit", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询批量分发上限
     *
     * @return 批量分发上限
     *         返回类型：BigInteger
     */
    public BigInteger getMaxBatchLimit() throws Exception {
        Function function = buildViewFunction("getMaxBatchLimit", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 预估建议最大扫描条数
     *
     * @param perRecordGas 单条 gas
     * @param fixedGas     固定 gas
     * @return 建议最大条数
     *         返回类型：BigInteger
     *         错误码：
     *         - INVALID_GAS_PARAM：预估参数非法（如 0）
     */
    public BigInteger estimateMaxCount(BigInteger perRecordGas, BigInteger fixedGas) throws Exception {
        Function function = new Function(
                "estimateMaxCount",
                List.of(uint256(perRecordGas), uint256(fixedGas)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询今日最大发行量
     *
     * @return 今日最大发行量
     *         返回类型：BigInteger
     *         错误码：
     *         - MINING_NOT_STARTED：挖矿未启动
     */
    public BigInteger getTodayMintable() throws Exception {
        Function function = buildViewFunction("getTodayMintable", List.of(new TypeReference<Uint256>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.getFirst().getValue();
    }

    /**
     * 查询当前年度剩余额度
     *
     * @return 当前年度剩余额度
     *         返回类型：CurrentYearRemaining（结构体）
     *         错误码：
     *         - MINING_NOT_STARTED：挖矿未启动
     */
    public CurrentYearRemaining getCurrentYearRemaining() throws Exception {
        Function function = buildViewFunction("getCurrentYearRemaining", List.of(new TypeReference<CurrentYearRemaining>() {}));
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (CurrentYearRemaining) outputs.getFirst();
    }

    // ===================== 锁仓/订单查询 =====================

    /**
     * 查询锁仓统计（全量）
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @return 锁仓统计
     *         返回类型：LockStats（结构体）
     *         错误码：
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位参数非法（非 1/2/3）
     */
    public LockStats getLockStats(String user, int lockType) throws Exception {
        Function function = new Function(
                "getLockStats",
                List.of(address(user), uint8(lockType)),
                List.of(new TypeReference<LockStats>() {})
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (LockStats) outputs.getFirst();
    }

    /**
     * 查询锁仓统计（分页）
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @param cursor 游标
     * @return 分页统计结果
     *         返回类型：LockStatsPaged
     *         错误码：
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位参数非法（非 1/2/3）
     */
    public LockStatsPaged getLockStatsPaged(String user, int lockType, BigInteger cursor) throws Exception {
        Function function = new Function(
                "getLockStatsPaged",
                List.of(address(user), uint8(lockType), uint256(cursor)),
                List.of(
                        new TypeReference<LockStats>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Bool>() {}
                )
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.size() < 4) {
            return null;
        }
        LockStats stats = (LockStats) outputs.get(0);
        BigInteger nextCursor = (BigInteger) outputs.get(1).getValue();
        BigInteger processed = (BigInteger) outputs.get(2).getValue();
        Boolean finished = (Boolean) outputs.get(3).getValue();
        return new LockStatsPaged(stats, nextCursor, processed, finished);
    }

    /**
     * 领取预览（仅 CLAIM）
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @return 预览结果
     *         返回类型：PreviewClaimable（结构体）
     *         错误码：
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位参数非法（非 1/2/3）
     */
    public PreviewClaimable previewClaimable(String user, int lockType) throws Exception {
        Function function = new Function(
                "previewClaimable",
                List.of(address(user), uint8(lockType)),
                List.of(new TypeReference<PreviewClaimable>() {})
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (PreviewClaimable) outputs.getFirst();
    }

    /**
     * 订单查询
     *
     * @param user 用户地址
     * @param orderId 订单号
     * @return 订单记录
     *         返回类型：OrderRecord（结构体）
     *         错误码：
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - ORDER_NOT_FOUND：订单不存在
     */
    public OrderRecord getOrder(String user, BigInteger orderId) throws Exception {
        Function function = new Function(
                "getOrder",
                List.of(address(user), uint256(orderId)),
                List.of(new TypeReference<OrderRecord>() {})
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (OrderRecord) outputs.getFirst();
    }

    /**
     * 查询用户是否授权管理员操作
     *
     * @param user 用户地址
     * @param operator 操作员地址（管理员地址）
     * @return 是否已授权
     *         返回类型：Boolean
     */
    public Boolean isOperatorApproved(String user, String operator) throws Exception {
        Function function = new Function(
                "isOperatorApproved",
                List.of(address(user), address(operator)),
                List.of(new TypeReference<Bool>() {})
        );
        List<Type<?>> outputs = callViewFunction(function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (Boolean) outputs.getFirst().getValue();
    }

    // ===================== 写操作 =====================

    /**
     * 开始挖矿（仅管理员）
     *
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     */
    public TransactionReceipt startMining() throws Exception {
        Function function = new Function("startMining", List.of(), List.of());
        return sendAionTransaction(function);
    }

    /**
     * 结算到当前年份
     *
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - MINING_NOT_STARTED：挖矿未启动
     */
    public TransactionReceipt settleToCurrentYear() throws Exception {
        Function function = new Function("settleToCurrentYear", List.of(), List.of());
        return sendAionTransaction(function);
    }

    /**
     * 分发额度（入仓/直接分发）
     *
     * @param to 用户地址
     * @param amount 数量（最小单位）
     * @param lockType 仓位类型（1/2/3）
     * @param distType 分发类型（1=入仓，2=直接分发）
     * @param orderId 订单号
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位非法
     *         - INVALID_DIST_TYPE：分发类型非法
     *         - ORDER_ID_DUPLICATE：订单号重复
     *         - ANNUAL_BUDGET_EXCEEDED：年度额度不足
     *         - ZERO_AMOUNT：数量为 0
     *         - CAP_EXCEEDED：超出总量上限
     */
    public TransactionReceipt allocateEmissionToLocks(
            String to,
            BigInteger amount,
            int lockType,
            int distType,
            BigInteger orderId
    ) throws Exception {
        Function function = new Function(
                "allocateEmissionToLocks",
                List.of(address(to), uint256(amount), uint8(lockType), uint8(distType), uint256(orderId)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 批量分发额度（入仓/直接分发）
     *
     * @param items 批量分发数据
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位非法
     *         - INVALID_DIST_TYPE：分发类型非法
     *         - ORDER_ID_DUPLICATE：订单号重复
     *         - ANNUAL_BUDGET_EXCEEDED：年度额度不足
     *         - ZERO_AMOUNT：数量为 0
     *         - INVALID_ADDRESS：地址非法（零地址）
     *         - EMPTY_BATCH：批量参数为空
     *         - BATCH_LIMIT_EXCEEDED：批量条数超过上限
     *         - CAP_EXCEEDED：超出总量上限
     */
    public TransactionReceipt allocateEmissionToLocksBatch(List<BatchItem> items) throws Exception {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("批量分发参数为空");
        }
        BigInteger maxBatchLimit = getMaxBatchLimit();
        if (maxBatchLimit != null && maxBatchLimit.signum() > 0) {
            BigInteger batchSize = BigInteger.valueOf(items.size());
            if (batchSize.compareTo(maxBatchLimit) > 0) {
                throw new IllegalArgumentException("批量条数超过上限: 当前=" + batchSize + ", 上限=" + maxBatchLimit);
            }
        }
        DynamicArray<BatchItem> batchItems = new DynamicArray<>(BatchItem.class, items);
        Function function = new Function(
                "allocateEmissionToLocksBatch",
                List.of(batchItems),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 管理员代用户领取（指定仓位）
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @param orderId 订单号
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位非法
     *         - ORDER_ID_DUPLICATE：订单号重复
     *         - NO_CLAIMABLE：本次无可领取数量
     *         - NOT_AUTHORIZED：未获得用户授权
     *         - CAP_EXCEEDED：超出总量上限
     */
    public TransactionReceipt claimAll(String user, int lockType, BigInteger orderId) throws Exception {
        Function function = new Function(
                "claimAll",
                List.of(address(user), uint8(lockType), uint256(orderId)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 兑换未解锁碎片
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @param targetAmount 兑换目标数量
     * @param orderId 订单号
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位非法
     *         - ORDER_ID_DUPLICATE：订单号重复
     *         - EXCHANGE_TARGET_NOT_MET：可兑换数量不足 target
     *         - NOT_AUTHORIZED：未获得用户授权
     *         - CAP_EXCEEDED：超出总量上限
     */
    public TransactionReceipt exchangeLockedFragment(
            String user,
            int lockType,
            BigInteger targetAmount,
            BigInteger orderId
    ) throws Exception {
        Function function = new Function(
                "exchangeLockedFragment",
                List.of(address(user), uint8(lockType), uint256(targetAmount), uint256(orderId)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 兑换已解锁碎片
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @param targetAmount 兑换目标数量
     * @param orderId 订单号
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     *         - MINING_NOT_STARTED：挖矿未启动
     *         - INVALID_LOCK_TYPE：仓位非法
     *         - ORDER_ID_DUPLICATE：订单号重复
     *         - EXCHANGE_TARGET_NOT_MET：可兑换数量不足 target
     *         - NOT_AUTHORIZED：未获得用户授权
     *         - CAP_EXCEEDED：超出总量上限
     */
    public TransactionReceipt exchangeUnlockedFragment(
            String user,
            int lockType,
            BigInteger targetAmount,
            BigInteger orderId
    ) throws Exception {
        Function function = new Function(
                "exchangeUnlockedFragment",
                List.of(address(user), uint8(lockType), uint256(targetAmount), uint256(orderId)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 用户授权管理员操作
     *
     * @param operator 操作员地址（管理员地址）
     * @param approved 是否授权
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - 文档未定义业务错误码（如需校验参数可新增）
     */
    public TransactionReceipt approveOperator(String operator, boolean approved) throws Exception {
        Function function = new Function(
                "approveOperator",
                List.of(address(operator), new Bool(approved)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 设置管理员（仅部署者）
     *
     * @param newAdmin 新管理员地址
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非部署者调用
     *         - INVALID_ADDRESS：新管理员为零地址
     */
    public TransactionReceipt setAdmin(String newAdmin) throws Exception {
        Function function = new Function(
                "setAdmin",
                List.of(address(newAdmin)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 设置扫描上限（仅管理员）
     *
     * @param limit 扫描上限
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     */
    public TransactionReceipt setMaxScanLimit(BigInteger limit) throws Exception {
        Function function = new Function(
                "setMaxScanLimit",
                List.of(uint256(limit)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * 设置批量分发上限（仅管理员）
     *
     * @param limit 批量分发上限
     * @return 交易回执
     *         返回类型：TransactionReceipt
     *         错误码：
     *         - NOT_ADMIN：非管理员调用
     */
    public TransactionReceipt setMaxBatchLimit(BigInteger limit) throws Exception {
        Function function = new Function(
                "setMaxBatchLimit",
                List.of(uint256(limit)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * ERC20 授权
     *
     * @param spender 被授权地址
     * @param amount 授权额度
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt approve(String spender, BigInteger amount) throws Exception {
        Function function = new Function(
                "approve",
                List.of(address(spender), uint256(amount)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * ERC20 转账
     *
     * @param to 接收地址
     * @param amount 转账数量
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt transfer(String to, BigInteger amount) throws Exception {
        Function function = new Function(
                "transfer",
                List.of(address(to), uint256(amount)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    /**
     * ERC20 代扣转账
     *
     * @param from 转出地址
     * @param to 接收地址
     * @param amount 转账数量
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt transferFrom(String from, String to, BigInteger amount) throws Exception {
        Function function = new Function(
                "transferFrom",
                List.of(address(from), address(to), uint256(amount)),
                List.of()
        );
        return sendAionTransaction(function);
    }

    // ===================== 结构体定义 =====================

    /**
     * 当前年度剩余额度
     */
    public static class CurrentYearRemaining extends StaticStruct {

        private final Uint256 yearRemaining;
        private final Uint256 budget;
        private final Uint256 minted;

        public CurrentYearRemaining(Uint256 yearRemaining, Uint256 budget, Uint256 minted) {
            super(yearRemaining, budget, minted);
            this.yearRemaining = yearRemaining;
            this.budget = budget;
            this.minted = minted;
        }

        /**
         * @return 当前年度剩余额度
         */
        public BigInteger getYearRemaining() {
            return yearRemaining.getValue();
        }

        /**
         * @return 当前年度预算
         */
        public BigInteger getBudget() {
            return budget.getValue();
        }

        /**
         * @return 当前年度已分发
         */
        public BigInteger getMinted() {
            return minted.getValue();
        }
    }

    /**
     * 批量分发入参
     */
    public static class BatchItem extends StaticStruct {

        private final Address to;
        private final Uint8 lockType;
        private final Uint8 distType;
        private final Uint256 amount;
        private final Uint256 orderId;

        public BatchItem(Address to, Uint8 lockType, Uint8 distType, Uint256 amount, Uint256 orderId) {
            super(to, lockType, distType, amount, orderId);
            this.to = to;
            this.lockType = lockType;
            this.distType = distType;
            this.amount = amount;
            this.orderId = orderId;
        }

        public BatchItem(String to, int lockType, int distType, BigInteger amount, BigInteger orderId) {
            this(address(to), uint8(lockType), uint8(distType), uint256(amount), uint256(orderId));
        }

        /**
         * @return 接收地址
         */
        public String getTo() {
            return to.getValue();
        }

        /**
         * @return 仓位类型（distType=1 时为 1/2/3；distType=2 时必须为 0）
         */
        public BigInteger getLockType() {
            return lockType.getValue();
        }

        /**
         * @return 分发类型（1=入仓，2=直接分发）
         */
        public BigInteger getDistType() {
            return distType.getValue();
        }

        /**
         * @return 分发数量（最小单位）
         */
        public BigInteger getAmount() {
            return amount.getValue();
        }

        /**
         * @return 订单号
         */
        public BigInteger getOrderId() {
            return orderId.getValue();
        }
    }

    /**
     * 锁仓统计
     */
    public static class LockStats extends StaticStruct {

        private final Uint256 totalCount;
        private final Uint256 totalAmount;
        private final Uint256 claimableCount;
        private final Uint256 claimableAmount;
        private final Uint256 unmaturedCount;
        private final Uint256 unmaturedAmount;
        private final Uint256 claimedCount;
        private final Uint256 claimedAmount;
        private final Uint256 fragmentedCount;
        private final Uint256 fragmentedAmount;
        private final Uint256 earliestUnlockTime;
        private final Uint256 latestUnlockTime;
        private final Uint256 lastIndex;

        public LockStats(
                Uint256 totalCount,
                Uint256 totalAmount,
                Uint256 claimableCount,
                Uint256 claimableAmount,
                Uint256 unmaturedCount,
                Uint256 unmaturedAmount,
                Uint256 claimedCount,
                Uint256 claimedAmount,
                Uint256 fragmentedCount,
                Uint256 fragmentedAmount,
                Uint256 earliestUnlockTime,
                Uint256 latestUnlockTime,
                Uint256 lastIndex
        ) {
            super(
                    totalCount,
                    totalAmount,
                    claimableCount,
                    claimableAmount,
                    unmaturedCount,
                    unmaturedAmount,
                    claimedCount,
                    claimedAmount,
                    fragmentedCount,
                    fragmentedAmount,
                    earliestUnlockTime,
                    latestUnlockTime,
                    lastIndex
            );
            this.totalCount = totalCount;
            this.totalAmount = totalAmount;
            this.claimableCount = claimableCount;
            this.claimableAmount = claimableAmount;
            this.unmaturedCount = unmaturedCount;
            this.unmaturedAmount = unmaturedAmount;
            this.claimedCount = claimedCount;
            this.claimedAmount = claimedAmount;
            this.fragmentedCount = fragmentedCount;
            this.fragmentedAmount = fragmentedAmount;
            this.earliestUnlockTime = earliestUnlockTime;
            this.latestUnlockTime = latestUnlockTime;
            this.lastIndex = lastIndex;
        }

        /**
         * @return 总记录数
         */
        public BigInteger getTotalCount() {
            return totalCount.getValue();
        }

        /**
         * @return 总额度
         */
        public BigInteger getTotalAmount() {
            return totalAmount.getValue();
        }

        /**
         * @return 可领取记录数
         */
        public BigInteger getClaimableCount() {
            return claimableCount.getValue();
        }

        /**
         * @return 可领取额度
         */
        public BigInteger getClaimableAmount() {
            return claimableAmount.getValue();
        }

        /**
         * @return 未成熟记录数
         */
        public BigInteger getUnmaturedCount() {
            return unmaturedCount.getValue();
        }

        /**
         * @return 未成熟额度
         */
        public BigInteger getUnmaturedAmount() {
            return unmaturedAmount.getValue();
        }

        /**
         * @return 已领取记录数
         */
        public BigInteger getClaimedCount() {
            return claimedCount.getValue();
        }

        /**
         * @return 已领取额度
         */
        public BigInteger getClaimedAmount() {
            return claimedAmount.getValue();
        }

        /**
         * @return 已兑换碎片记录数
         */
        public BigInteger getFragmentedCount() {
            return fragmentedCount.getValue();
        }

        /**
         * @return 已兑换碎片额度
         */
        public BigInteger getFragmentedAmount() {
            return fragmentedAmount.getValue();
        }

        /**
         * @return 最早解锁时间
         */
        public BigInteger getEarliestUnlockTime() {
            return earliestUnlockTime.getValue();
        }

        /**
         * @return 最晚解锁时间
         */
        public BigInteger getLatestUnlockTime() {
            return latestUnlockTime.getValue();
        }

        /**
         * @return 最后索引
         */
        public BigInteger getLastIndex() {
            return lastIndex.getValue();
        }
    }

    /**
     * 领取预览结构
     */
    public static class PreviewClaimable extends StaticStruct {

        private final Uint256 claimable;
        private final Uint256 burnAmount;
        private final Uint256 netAmount;
        private final Uint256 processed;
        private final Uint256 nextCursor;

        public PreviewClaimable(
                Uint256 claimable,
                Uint256 burnAmount,
                Uint256 netAmount,
                Uint256 processed,
                Uint256 nextCursor
        ) {
            super(claimable, burnAmount, netAmount, processed, nextCursor);
            this.claimable = claimable;
            this.burnAmount = burnAmount;
            this.netAmount = netAmount;
            this.processed = processed;
            this.nextCursor = nextCursor;
        }

        /**
         * @return 可领取总额
         */
        public BigInteger getClaimable() {
            return claimable.getValue();
        }

        /**
         * @return 本次应销毁数量
         */
        public BigInteger getBurnAmount() {
            return burnAmount.getValue();
        }

        /**
         * @return 实际到账数量
         */
        public BigInteger getNetAmount() {
            return netAmount.getValue();
        }

        /**
         * @return 本次处理条数
         */
        public BigInteger getProcessed() {
            return processed.getValue();
        }

        /**
         * @return 下一游标位置
         */
        public BigInteger getNextCursor() {
            return nextCursor.getValue();
        }
    }

    /**
     * 订单记录结构
     */
    public static class OrderRecord extends StaticStruct {

        private final Uint8 methodType;
        private final Address user;
        private final Uint8 lockType;
        private final Uint256 amount;
        private final Uint256 executedAmount;
        private final Uint256 netAmount;
        private final Uint256 burnAmount;
        private final Uint256 timestamp;
        private final Uint8 status;

        public OrderRecord(
                Uint8 methodType,
                Address user,
                Uint8 lockType,
                Uint256 amount,
                Uint256 executedAmount,
                Uint256 netAmount,
                Uint256 burnAmount,
                Uint256 timestamp,
                Uint8 status
        ) {
            super(methodType, user, lockType, amount, executedAmount, netAmount, burnAmount, timestamp, status);
            this.methodType = methodType;
            this.user = user;
            this.lockType = lockType;
            this.amount = amount;
            this.executedAmount = executedAmount;
            this.netAmount = netAmount;
            this.burnAmount = burnAmount;
            this.timestamp = timestamp;
            this.status = status;
        }

        /**
         * @return 方法类型
         */
        public BigInteger getMethodType() {
            return methodType.getValue();
        }

        /**
         * @return 用户地址
         */
        public String getUser() {
            return user.getValue();
        }

        /**
         * @return 仓位类型
         */
        public BigInteger getLockType() {
            return lockType.getValue();
        }

        /**
         * @return 数量入参
         */
        public BigInteger getAmount() {
            return amount.getValue();
        }

        /**
         * @return 实际执行数量
         */
        public BigInteger getExecutedAmount() {
            return executedAmount.getValue();
        }

        /**
         * @return 实际到账数量
         */
        public BigInteger getNetAmount() {
            return netAmount.getValue();
        }

        /**
         * @return 本次销毁数量
         */
        public BigInteger getBurnAmount() {
            return burnAmount.getValue();
        }

        /**
         * @return 执行时间
         */
        public BigInteger getTimestamp() {
            return timestamp.getValue();
        }

        /**
         * @return 执行状态
         */
        public BigInteger getStatus() {
            return status.getValue();
        }
    }

    /**
     * 锁仓统计分页结果
     *
     * @param stats      -- GETTER --
     *                   锁仓统计
     * @param nextCursor -- GETTER --
     *                   下次游标
     * @param processed  -- GETTER --
     *                   本次处理条数
     * @param finished   -- GETTER --
     *                   是否完成
     */
        public record LockStatsPaged(LockStats stats, BigInteger nextCursor, BigInteger processed, Boolean finished) {

    }
}
