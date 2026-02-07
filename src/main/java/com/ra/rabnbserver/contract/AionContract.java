package com.ra.rabnbserver.contract;

import com.ra.rabnbserver.contract.support.BlockchainProperties;
import com.ra.rabnbserver.contract.support.ContractAddressProperties;
import com.ra.rabnbserver.contract.support.ContractBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.List;

import static com.ra.rabnbserver.contract.support.ContractTypeUtils.address;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint256;
import static com.ra.rabnbserver.contract.support.ContractTypeUtils.uint8;

/**
 * AION 合约管理员调用接口（新版 AiRword）
 *
 * 说明：
 * - 锁仓周期在测试合约中已调整为分钟级（1/2/4 分钟），lockType 仍为 1/2/3。
 * - 读写接口均基于 air.md ABI 定义实现。
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
     * 获取合约地址
     *
     * @return 合约地址
     */
    public String getAddress() {
        return contractAddressProperties.getAion();
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
     * 查询精度
     *
     * @return 精度
     *         返回类型：BigInteger
     */
    public BigInteger decimals() throws Exception {
        Function function = buildViewFunction("decimals", List.of(new TypeReference<Uint8>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询总供应量
     *
     * @return 总供应量
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
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
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
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询合约部署者地址
     *
     * @return 部署者地址
     *         返回类型：String
     */
    public String owner() throws Exception {
        Function function = buildViewFunction("owner", List.of(new TypeReference<Address>() {}));
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
     */
    public String admin() throws Exception {
        Function function = buildViewFunction("admin", List.of(new TypeReference<Address>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0).getValue().toString();
    }

    /**
     * 查询社区地址
     *
     * @return 社区地址
     *         返回类型：String
     */
    public String community() throws Exception {
        Function function = buildViewFunction("community", List.of(new TypeReference<Address>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return outputs.get(0).getValue().toString();
    }

    /**
     * 查询 CAP（最大总量）
     *
     * @return CAP
     *         返回类型：BigInteger
     */
    public BigInteger cap() throws Exception {
        Function function = buildViewFunction("CAP", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询挖矿开始时间
     *
     * @return 挖矿开始时间戳
     *         返回类型：BigInteger
     */
    public BigInteger miningStart() throws Exception {
        Function function = buildViewFunction("miningStart", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询已结算年份
     *
     * @return 已结算年份
     *         返回类型：BigInteger
     */
    public BigInteger lastSettledYear() throws Exception {
        Function function = buildViewFunction("lastSettledYear", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询当前年度预算
     *
     * @return 年度预算
     *         返回类型：BigInteger
     */
    public BigInteger yearBudget() throws Exception {
        Function function = buildViewFunction("yearBudget", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询当前年度已分发
     *
     * @return 已分发数量
     *         返回类型：BigInteger
     */
    public BigInteger yearMinted() throws Exception {
        Function function = buildViewFunction("yearMinted", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询剩余可挖额度
     *
     * @return 剩余可挖额度
     *         返回类型：BigInteger
     */
    public BigInteger remainingCap() throws Exception {
        Function function = buildViewFunction("remainingCap", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询年度开始时间
     *
     * @return 年度开始时间戳
     *         返回类型：BigInteger
     */
    public BigInteger yearStartTs() throws Exception {
        Function function = buildViewFunction("yearStartTs", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询扫描上限
     *
     * @return 扫描上限
     *         返回类型：BigInteger
     */
    public BigInteger getMaxScanLimit() throws Exception {
        Function function = buildViewFunction("getMaxScanLimit", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 预估建议最大扫描条数
     *
     * @param perRecordGas 单条 gas
     * @param fixedGas     固定 gas
     * @return 建议最大条数
     *         返回类型：BigInteger
     */
    public BigInteger estimateMaxCount(BigInteger perRecordGas, BigInteger fixedGas) throws Exception {
        Function function = new Function(
                "estimateMaxCount",
                List.of(uint256(perRecordGas), uint256(fixedGas)),
                List.of(new TypeReference<Uint256>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    /**
     * 查询今日最大发行量
     *
     * @return 今日最大发行量
     *         返回类型：BigInteger
     */
    public BigInteger getTodayMintable() throws Exception {
        Function function = buildViewFunction("getTodayMintable", List.of(new TypeReference<Uint256>() {}));
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (BigInteger) outputs.get(0).getValue();
    }

    // ===================== 锁仓/订单查询 =====================

    /**
     * 查询锁仓统计（全量）
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @return 锁仓统计
     *         返回类型：LockStats（结构体）
     */
    public LockStats getLockStats(String user, int lockType) throws Exception {
        Function function = new Function(
                "getLockStats",
                List.of(address(user), uint8(lockType)),
                List.of(new TypeReference<LockStats>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (LockStats) outputs.get(0);
    }

    /**
     * 查询锁仓统计（分页）
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @param cursor 游标
     * @return 分页统计结果
     *         返回类型：LockStatsPaged
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
        List<Type> outputs = callFunction(getAddress(), function);
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
     */
    public PreviewClaimable previewClaimable(String user, int lockType) throws Exception {
        Function function = new Function(
                "previewClaimable",
                List.of(address(user), uint8(lockType)),
                List.of(new TypeReference<PreviewClaimable>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (PreviewClaimable) outputs.get(0);
    }

    /**
     * 订单查询
     *
     * @param user 用户地址
     * @param orderId 订单号
     * @return 订单记录
     *         返回类型：OrderRecord（结构体）
     */
    public OrderRecord getOrder(String user, BigInteger orderId) throws Exception {
        Function function = new Function(
                "getOrder",
                List.of(address(user), uint256(orderId)),
                List.of(new TypeReference<OrderRecord>() {})
        );
        List<Type> outputs = callFunction(getAddress(), function);
        if (outputs.isEmpty()) {
            return null;
        }
        return (OrderRecord) outputs.get(0);
    }

    // ===================== 写操作 =====================

    /**
     * 开始挖矿（仅管理员）
     *
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt startMining() throws Exception {
        Function function = new Function("startMining", List.of(), List.of());
        return sendTransaction(getAddress(), function);
    }

    /**
     * 结算到当前年份
     *
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt settleToCurrentYear() throws Exception {
        Function function = new Function("settleToCurrentYear", List.of(), List.of());
        return sendTransaction(getAddress(), function);
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
        return sendTransaction(getAddress(), function);
    }

    /**
     * 管理员代用户领取（指定仓位）
     *
     * @param user 用户地址
     * @param lockType 仓位类型（1/2/3）
     * @param orderId 订单号
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt claimAll(String user, int lockType, BigInteger orderId) throws Exception {
        Function function = new Function(
                "claimAll",
                List.of(address(user), uint8(lockType), uint256(orderId)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
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
        return sendTransaction(getAddress(), function);
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
        return sendTransaction(getAddress(), function);
    }

    /**
     * 用户授权管理员操作
     *
     * @param operator 操作员地址（管理员地址）
     * @param approved 是否授权
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt approveOperator(String operator, boolean approved) throws Exception {
        Function function = new Function(
                "approveOperator",
                List.of(address(operator), new Bool(approved)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
    }

    /**
     * 设置管理员（仅部署者）
     *
     * @param newAdmin 新管理员地址
     * @return 交易回执
     *         返回类型：TransactionReceipt
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
     * 设置扫描上限（仅管理员）
     *
     * @param limit 扫描上限
     * @return 交易回执
     *         返回类型：TransactionReceipt
     */
    public TransactionReceipt setMaxScanLimit(BigInteger limit) throws Exception {
        Function function = new Function(
                "setMaxScanLimit",
                List.of(uint256(limit)),
                List.of()
        );
        return sendTransaction(getAddress(), function);
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
        return sendTransaction(getAddress(), function);
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
        return sendTransaction(getAddress(), function);
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
        return sendTransaction(getAddress(), function);
    }

    // ===================== 结构体定义 =====================

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
     */
    public static class LockStatsPaged {

        private final LockStats stats;
        private final BigInteger nextCursor;
        private final BigInteger processed;
        private final Boolean finished;

        public LockStatsPaged(LockStats stats, BigInteger nextCursor, BigInteger processed, Boolean finished) {
            this.stats = stats;
            this.nextCursor = nextCursor;
            this.processed = processed;
            this.finished = finished;
        }

        /**
         * @return 锁仓统计
         */
        public LockStats getStats() {
            return stats;
        }

        /**
         * @return 下次游标
         */
        public BigInteger getNextCursor() {
            return nextCursor;
        }

        /**
         * @return 本次处理条数
         */
        public BigInteger getProcessed() {
            return processed;
        }

        /**
         * @return 是否完成
         */
        public Boolean getFinished() {
            return finished;
        }
    }
}
