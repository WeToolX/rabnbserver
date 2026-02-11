package com.ra.rabnbserver;

import com.ra.rabnbserver.contract.AionContract;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.contract.service.AionService;
import com.ra.rabnbserver.contract.support.AmountConvertUtils;
import com.ra.rabnbserver.exception.AionContractException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.sql.BatchUpdateException;
import java.util.List;

@SpringBootTest
@Slf4j
class RabnbserverApplicationTests {

    @Autowired
    private PaymentUsdtContract paymentUsdtContract;

    @Autowired
    private AionContract aionContract;

    @Autowired
    private CardNftContract cardNftContract;

    @Autowired
    private AionService aionService;

    /**
     * 方法作用：基础上下文启动校验
     */
    @Test
    void contextLoads() {
    }

    // ===================== 通用工具 =====================
    /**
     * 方法作用：校验 TODO 字符串是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private String requireTodoString(String label, String value) {
        if (value == null || value.startsWith("TODO")) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：校验 TODO 金额是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private BigDecimal requireTodoAmount(String label, BigDecimal value) {
        if (value == null) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：校验 TODO 原始数量是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private BigInteger requireTodoRaw(String label, BigInteger value) {
        if (value == null) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：校验 TODO 列表是否已填写
     *
     * @param label 提示标签
     * @param value 参数值
     * @return 参数值
     */
    private List<String> requireTodoList(String label, List<String> value) {
        if (value == null || value.isEmpty() || "TODO".equals(value.get(0))) {
            throw new IllegalStateException("TODO: 请设置 " + label);
        }
        return value;
    }

    /**
     * 方法作用：生成订单号（bytes32 hex）
     *
     * @return 订单号
     */
    private String generateOrderIdHex() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Numeric.toHexString(bytes);
    }

    /**
     * 方法作用：生成订单号（uint256）
     *
     * @return 订单号
     */
    private BigInteger generateOrderId() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return new BigInteger(1, bytes);
    }
    @Test
    void getod(){
        log.info(generateOrderId().toString());
    }

    // ===================== AionContract（只读）=====================

    /**
     * 方法作用：查询 AION 基础信息
     */
    @Test
    void testAionMeta() throws Exception {
        log.info("AION 名称: {}", aionContract.name());
        log.info("AION 符号: {}", aionContract.symbol());
        log.info("AION 精度: {}", aionContract.decimals());
        log.info("AION 最大总量: {}", aionContract.cap());
        log.info("AION 总供应量: {}", aionContract.totalSupply());
    }

    /**
     * 方法作用：查询合约角色地址
     */
    @Test
    void testAionAddresses() throws Exception {
        log.info("AION 部署者地址: {}", aionContract.owner());
        log.info("AION 管理员地址: {}", aionContract.admin());
        log.info("AION 社区地址: {}", aionContract.community());
    }

    /**
     * 方法作用：查询挖矿状态变量
     */
    @Test
    void testAionMiningState() throws Exception {
        log.info("AION 挖矿开始时间: {}", aionContract.miningStart());
        log.info("AION 已结算年份: {}", aionContract.lastSettledYear());
        log.info("AION 当前年度预算: {}", aionContract.yearBudget());
        log.info("AION 当前年度已分发: {}", aionContract.yearMinted());
        log.info("AION 剩余可挖额度: {}", aionContract.remainingCap());
        log.info("AION 当前年度起始时间: {}", aionContract.yearStartTs());
    }

    /**
     * 方法作用：查询扫描上限
     */
    @Test
    void testAionMaxScanLimit() throws Exception {
        log.info("AION 扫描上限: {}", aionContract.getMaxScanLimit());
    }

    /**
     * 方法作用：查询批量分发上限
     */
    @Test
    void testAionMaxBatchLimit() throws Exception {
        log.info("AION 批量分发上限: {}", aionContract.getMaxBatchLimit());
    }

    /**
     * 方法作用：预估建议最大扫描条数
     */
    @Test
    void testAionEstimateMaxCount() throws Exception {
        BigInteger perRecordGas = requireTodoRaw("AION perRecordGas", null);
        BigInteger fixedGas = requireTodoRaw("AION fixedGas", null);
        log.info("AION 建议最大扫描条数: {}", aionContract.estimateMaxCount(perRecordGas, fixedGas));
    }

    /**
     * 方法作用：查询今日最大发行量
     */
    @Test
    void testAionTodayMintable() throws Exception {
        log.info("AION 今日最大发行量: {}", aionContract.getTodayMintable());
    }

    /**
     * 方法作用：查询当前年度剩余额度
     */
    @Test
    void testAionCurrentYearRemaining() throws Exception {
        AionContract.CurrentYearRemaining remaining = aionContract.getCurrentYearRemaining();
        if (remaining == null) {
            log.info("AION 当前年度剩余额度未返回");
            return;
        }
        log.info("AION 当前年度剩余额度: {}", remaining.getYearRemaining());
        log.info("AION 当前年度预算: {}", remaining.getBudget());
        log.info("AION 当前年度已分发: {}", remaining.getMinted());
    }

    /**
     * 方法作用：查询指定地址余额
     */
    @Test
    void testAionBalanceOf() throws Exception {
        String account = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        log.info("AION 余额({}): {}", account, aionContract.balanceOf(account));
    }

    /**
     * 方法作用：查询授权额度
     */
    @Test
    void testAionAllowance() throws Exception {
        String owner = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        String spender = "0xa068802d54d2aca1ad8ce6f2300eee02e3b50113";
        log.info("AION 授权额度({},{}): {}", owner, spender, aionContract.allowance(owner, spender));
    }

    /**
     * 方法作用：查询锁仓统计（全量）
     */
    @Test
    void testAionLockStats() throws Exception {
        String user = "0xE7229d10B5E6014cA8F586963eF6E0784F7735B2";
        int lockType = 1; // TODO: 1/2/3（测试合约为 1/2/4 分钟）
        AionContract.LockStats stats = aionContract.getLockStats(user, lockType);
        if (stats == null) {
            log.info("AION 锁仓统计未返回，user={}, lockType={}", user, lockType);
            return;
        }
        log.info("AION 锁仓统计-总记录数: {}", stats.getTotalCount());
        log.info("AION 锁仓统计-总额度: {}", stats.getTotalAmount());
        log.info("AION 锁仓统计-可领取记录数: {}", stats.getClaimableCount());
        log.info("AION 锁仓统计-可领取额度: {}", stats.getClaimableAmount());
        log.info("AION 锁仓统计-未到期记录数: {}", stats.getUnmaturedCount());
        log.info("AION 锁仓统计-未到期额度: {}", stats.getUnmaturedAmount());
        log.info("AION 锁仓统计-已领取记录数: {}", stats.getClaimedCount());
        log.info("AION 锁仓统计-已领取额度: {}", stats.getClaimedAmount());
        log.info("AION 锁仓统计-已兑换碎片记录数: {}", stats.getFragmentedCount());
        log.info("AION 锁仓统计-已兑换碎片额度: {}", stats.getFragmentedAmount());
        log.info("AION 锁仓统计-最近可解锁时间: {}", stats.getEarliestUnlockTime());
        log.info("AION 锁仓统计-最晚解锁时间: {}", stats.getLatestUnlockTime());
        log.info("AION 锁仓统计-最后索引: {}", stats.getLastIndex());
    }

    /**
     * 方法作用：查询锁仓统计（分页）
     */
    @Test
    void testAionLockStatsPaged() throws Exception {
        String user = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        int lockType = 1; // TODO: 1/2/3（测试合约为 1/2/4 分钟）
        BigInteger cursor = BigInteger.ZERO;
        int round = 0;
        log.info("AION 锁仓分页统计开始，user={}, lockType={}, cursor={}", user, lockType, cursor);
        while (true) {
            round++;
            AionContract.LockStatsPaged paged = aionContract.getLockStatsPaged(user, lockType, cursor);
            if (paged == null) {
                log.info("AION 锁仓分页统计未返回，user={}, lockType={}, cursor={}", user, lockType, cursor);
                break;
            }
            log.info("AION 锁仓分页统计-第{}页-处理条数: {}", round, paged.processed());
            log.info("AION 锁仓分页统计-第{}页-下次游标: {}", round, paged.nextCursor());
            log.info("AION 锁仓分页统计-第{}页-是否完成: {}", round, paged.finished());
            if (Boolean.TRUE.equals(paged.finished())) {
                log.info("AION 锁仓分页统计完成，最后游标: {}", paged.nextCursor());
                break;
            }
            if (paged.nextCursor() == null) {
                log.info("AION 锁仓分页统计中断：下次游标为空，当前游标: {}", cursor);
                break;
            }
            if (paged.nextCursor().compareTo(cursor) <= 0) {
                log.info("AION 锁仓分页统计中断：下次游标未前进，当前游标={}, 下次游标={}",
                        cursor, paged.nextCursor());
                break;
            }
            cursor = paged.nextCursor();
        }
    }

    /**
     * 方法作用：领取预览
     */
    @Test
    void testAionPreviewClaimable() throws Exception {
        String user = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        int lockType = 1; // TODO: 1/2/3（测试合约为 1/2/4 分钟）
        AionContract.PreviewClaimable preview = aionContract.previewClaimable(user, lockType);
        if (preview == null) {
            log.info("AION 领取预览未返回，user={}, lockType={}", user, lockType);
            return;
        }
        log.info("AION 领取预览-可领取: {}", preview.getClaimable());
        log.info("AION 领取预览-销毁数量: {}", preview.getBurnAmount());
        log.info("AION 领取预览-到账数量: {}", preview.getNetAmount());
        log.info("AION 领取预览-处理条数: {}", preview.getProcessed());
        log.info("AION 领取预览-下次游标: {}", preview.getNextCursor());
    }

    /**
     * 方法作用：订单查询
     */
    @Test
    void testAionGetOrder() throws Exception {
        try {
            String user = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
            BigInteger orderId = new BigInteger("123");
            AionContract.OrderRecord record = aionContract.getOrder(user, orderId);
            if (record == null) {
                log.info("AION 订单未返回，user={}, orderId={}", user, orderId);
                return;
            }
            log.info("AION 订单-方法类型: {}", record.getMethodType());
            log.info("AION 订单-仓位: {}", record.getLockType());
            log.info("AION 订单-入参数量: {}", record.getAmount());
            log.info("AION 订单-执行数量: {}", record.getExecutedAmount());
            log.info("AION 订单-到账数量: {}", record.getNetAmount());
            log.info("AION 订单-销毁数量: {}", record.getBurnAmount());
            log.info("AION 订单-时间戳: {}", record.getTimestamp());
        } catch (AionContractException e) {
            log.error(e.getDecodedDetail());
        }

    }

    /**
     * 方法作用：查询用户是否授权管理员操作
     */
    @Test
    void testAionIsOperatorApproved() throws Exception {
        String user = requireTodoString("AION isOperatorApproved user", "TODO:填写地址");
        String operator = requireTodoString("AION isOperatorApproved operator", "TODO:填写地址");
        log.info("AION 是否授权({}, {}): {}", user, operator, aionContract.isOperatorApproved(user, operator));
    }

    /**
     * 方法作用：查询合约自身余额
     */
    @Test
    void testAionBalanceOfSelf() throws Exception {
        String self = aionContract.getAddress();
        log.info("AION 合约自身余额: {}", aionContract.balanceOf(self));
    }

    // ===================== AionContract（写操作）=====================

    /**
     * 方法作用：开始挖矿
     */
    @Test
    void testAionStartMining() throws Exception {
        var receipt = aionContract.startMining();
        log.info("开始挖矿结果: {}", receipt);
    }

    /**
     * 方法作用：结算到当前年份
     */
    @Test
    void testAionSettleToCurrentYear() throws Exception {
        var receipt = aionContract.settleToCurrentYear();
        log.info("结算到当前年份结果: {}", receipt);
    }

    /**
     * 方法作用：分发额度（入仓/直接分发）
     */
    @Test
    void testAionAllocateEmissionToLocks() throws Exception {
        String to = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        BigDecimal amountHuman = BigDecimal.valueOf(1000);
        int lockType = 1; // TODO: 1/2/3（测试合约为 1/2/4 分钟）
        int distType = 1; // TODO: 1=入仓 2=直接分发
        BigInteger orderId = generateOrderId();
        BigInteger amount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.AION, amountHuman);
        log.info("分发 AION，地址: {}, 数量(原始): {}, 仓位: {}, 分发类型: {}, 订单号: {}",
                to, amount, lockType, distType, orderId);
        var receipt = aionContract.allocateEmissionToLocks(to, amount, lockType, distType, orderId);
        log.info("分发入仓结果: {}", receipt);
    }

    /**
     * 方法作用：批量分发额度（入仓/直接分发）
     */
    @Test
    void testAionAllocateEmissionToLocksBatch() throws Exception {
        String to = requireTodoString("AION batch to", "TODO:填写地址");
        BigInteger orderId = requireTodoRaw("AION 批量 订单号(每用户一个)", null);
        BigInteger l1Amount = requireTodoRaw("AION 批量 L1 数量(三位小数整数)", null);
        BigInteger l2Amount = BigInteger.ZERO; // 为空则填 0
        BigInteger l3Amount = BigInteger.ZERO;
        BigInteger directAmount = requireTodoRaw("AION 批量 Direct 数量(三位小数整数)", null);

        List<String> tos = List.of(to);
        List<AionContract.BatchData> dataList = List.of(
                new AionContract.BatchData(
                        orderId,
                        l1Amount,
                        l2Amount,
                        l3Amount,
                        directAmount
                )
        );
        log.info("批量分发 AION，订单号: {}, L1数量: {}, Direct数量: {}", orderId, l1Amount, directAmount);
        var receipt = aionContract.allocateEmissionToLocksBatch(tos, dataList);
        log.info("批量分发结果: {}", receipt);
    }


    /**
     * 方法作用：结构化压力测试 - 900 条数据批量发送（适配新版 BatchData 结构）
     * 结构：地址(大循环) -> 类型(中循环) -> 条数(小循环)
     */
    @Test
    void testAionStructuredBatchDistribute() throws Exception {
        // 1. 基础配置
        List<String> targetAddresses = List.of(
                "0xE7229d10B5E6014cA8F586963eF6E0784F7735B2"
        );
        int[] lockTypes = {1, 2, 3,0};
        int countPerConfig = 1;

        BigDecimal amountPerRecord = new BigDecimal("1000");
        BigInteger unitDivisor = BigInteger.valueOf(10).pow(15);
        BigInteger rawAmountWei = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.AION, amountPerRecord);
        BigInteger chainAmount = rawAmountWei.divide(unitDivisor);

        // 2. 构造数据列表
        List<String> tos = new java.util.ArrayList<>();
        List<AionContract.BatchData> dataList = new java.util.ArrayList<>();

        log.info("开始构造新版 BatchData 数据包...");
        for (String address : targetAddresses) {
                for (int i = 0; i < countPerConfig; i++) {
                    BigInteger l1Amt = BigInteger.ZERO;
                    BigInteger l2Amt = BigInteger.ZERO;
                    BigInteger l3Amt = BigInteger.ZERO;
                    BigInteger directAmt = BigInteger.ZERO;
                    BigInteger orderId = BigInteger.valueOf(System.nanoTime() + i);
                    for (int lockType : lockTypes) {
                        switch (lockType) {
                            case 1 -> l1Amt = chainAmount;
                            case 2 -> l2Amt = chainAmount;
                            case 3 -> l3Amt = chainAmount;
                            case 0 -> directAmt = chainAmount;
                        }
                    }
                    tos.add(address);
                    dataList.add(new AionContract.BatchData(
                            orderId, l1Amt, l2Amt, l3Amt, directAmt
                    ));
            }
        }

        log.info("数据构造完成，待发送地址数: {}, BatchData数: {}", tos.size(), dataList.size());

        // ==========================================
        // 【新增】字节长度校验逻辑
        // ==========================================
        try {
            // 手动构造 Web3j Function 对象以模拟编码
            org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                    "allocateEmissionToLocksBatch",
                    java.util.Arrays.asList(
                            new org.web3j.abi.datatypes.DynamicArray<>(
                                    org.web3j.abi.datatypes.Address.class,
                                    tos.stream().map(org.web3j.abi.datatypes.Address::new).collect(java.util.stream.Collectors.toList())
                            ),
                            new org.web3j.abi.datatypes.DynamicArray<>(
                                    AionContract.BatchData.class,
                                    dataList
                            )
                    ),
                    java.util.Collections.emptyList()
            );

            String encodedFunction = org.web3j.abi.FunctionEncoder.encode(function);
            int txSizeBytes = (encodedFunction.length() - 2) / 2; // 除去 0x，每两个 hex 字符为一个字节
            int limit = 131072; // 节点硬限制 128KB

            log.info("【字节校验】当前交易预估大小: {} bytes, 限制: {} bytes", txSizeBytes, limit);

            if (txSizeBytes > limit) {
                log.error("【拦截】交易数据量过大 ({} bytes)，将导致 Oversized Data 错误！请调小 countPerConfig。", txSizeBytes);
                return; // 直接结束测试，不调用链上接口
            }
        } catch (Exception e) {
            log.warn("【字节校验】编码计算失败，跳过校验继续执行。Reason: {}", e.getMessage());
        }
        // ==========================================

        // 3. 发送交易
        try {
            log.info("正在发送新版批量分发交易...");
            TransactionReceipt receipt = aionContract.allocateEmissionToLocksBatch(tos, dataList);

            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                log.info("【成功】批量分发完成！Hash: {}", receipt.getTransactionHash());
                log.info("总计消耗 Gas: {}", receipt.getGasUsed());
            } else {
                String reason = receipt != null ? receipt.getRevertReason() : "No Receipt";
                log.error("【失败】交易执行回退。Reason: {}", reason);
            }
        } catch (AionContractException e) {
            log.error("【合约异常】Code: {}, Name: {}, Detail: {}", e.getErrorCode(), e.getErrorName(), e.getDecodedDetail());
        } catch (Exception e) {
            log.error("【系统异常】", e);
        }
    }

    /**
     * 方法作用：管理员代用户领取
     */
    @Test
    void testAionClaimAll() throws Exception {
        String user = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        int lockType = 1; // TODO: 1/2/3（测试合约为 1/2/4 分钟）
        BigInteger orderId = generateOrderId();
        var receipt = aionContract.claimAll(user, lockType, orderId);
        log.info("代用户领取结果: {}", receipt);
    }

    /**
     * 方法作用：兑换未解锁碎片
     */
    @Test
    void testAionExchangeLockedFragment() throws Exception {
        String user = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        int lockType = 1; // TODO: 1/2/3（测试合约为 1/2/4 分钟）
        BigInteger targetAmount = requireTodoRaw("AION exchangeLockedFragment 目标数量", null);
        BigInteger orderId = requireTodoRaw("AION exchangeLockedFragment orderId", null);
        var receipt = aionContract.exchangeLockedFragment(user, lockType, targetAmount, orderId);
        log.info("兑换未解锁碎片结果: {}", receipt);
    }

    /**
     * 方法作用：兑换已解锁碎片
     */
    @Test
    void testAionExchangeUnlockedFragment() throws Exception {
        String user = "0x6aDA2D643b850f179146F3979a5Acf613aBEA3FF";
        int lockType = 1; // TODO: 1/2/3（测试合约为 1/2/4 分钟）
        BigInteger targetAmount = new BigInteger("50000000000000000000");
        BigInteger orderId = generateOrderId();
        var receipt = aionContract.exchangeUnlockedFragment(user, lockType, targetAmount, orderId);
        log.info("兑换已解锁碎片结果: {}", receipt);
    }

    /**
     * 方法作用：用户授权管理员操作
     */
    @Test
    void testAionApproveOperator() throws Exception {
        String operator = requireTodoString("AION approveOperator 地址", "TODO:填写地址");
        boolean approved = true; // TODO: true/false
        var receipt = aionContract.approveOperator(operator, approved);
        log.info("授权管理员操作结果: {}", receipt);
    }

    /**
     * 方法作用：设置管理员地址
     */
    @Test
    void testAionSetAdmin() throws Exception {
        String newAdmin = requireTodoString("AION setAdmin 地址", "TODO:填写地址");
        var receipt = aionContract.setAdmin(newAdmin);
        log.info("设置管理员结果: {}", receipt);
    }

    /**
     * 方法作用：设置扫描上限
     */
    @Test
    void testAionSetMaxScanLimit() throws Exception {
        BigInteger limit = requireTodoRaw("AION setMaxScanLimit", null);
        var receipt = aionContract.setMaxScanLimit(limit);
        log.info("设置扫描上限结果: {}", receipt);
    }

    /**
     * 方法作用：设置批量分发上限
     */
    @Test
    void testAionSetMaxBatchLimit() throws Exception {
        BigInteger limit = new BigInteger("10000");
        var receipt = aionContract.setMaxBatchLimit(limit);
        log.info("设置批量分发上限结果: {}", receipt);
    }

    /**
     * 方法作用：ERC20 授权
     */
    @Test
    void testAionApprove() throws Exception {
        String spender = requireTodoString("AION approve 地址", "TODO:填写地址");
        BigDecimal amountHuman = requireTodoAmount("AION approve 数量", null);
        BigInteger amount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.AION, amountHuman);
        var receipt = aionContract.approve(spender, amount);
        log.info("ERC20 授权结果: {}", receipt);
    }

    /**
     * 方法作用：ERC20 转账
     */
    @Test
    void testAionTransfer() throws Exception {
        String to = requireTodoString("AION transfer 地址", "TODO:填写地址");
        BigDecimal amountHuman = requireTodoAmount("AION transfer 数量", null);
        BigInteger amount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.AION, amountHuman);
        var receipt = aionContract.transfer(to, amount);
        log.info("ERC20 转账结果: {}", receipt);
    }

    /**
     * 方法作用：ERC20 代扣转账
     */
    @Test
    void testAionTransferFrom() throws Exception {
        String from = requireTodoString("AION transferFrom from", "TODO:填写地址");
        String to = requireTodoString("AION transferFrom to", "TODO:填写地址");
        BigDecimal amountHuman = requireTodoAmount("AION transferFrom 数量", null);
        BigInteger amount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.AION, amountHuman);
        var receipt = aionContract.transferFrom(from, to, amount);
        log.info("ERC20 代扣转账结果: {}", receipt);
    }

    // ===================== AionService ======================

    /**
     * 方法作用：查询可流通量
     */
    @Test
    void testAionCirculatingSupply() throws Exception {
        log.info("AION 可流通量: {}", aionService.queryCirculatingSupply());
    }

    /**
     * 方法作用：查询指定地址的兑换记录汇总
     */
    @Test
    void testAionExchangePaidSummary() throws Exception {
        String user = requireTodoString("AION ExchangePaid 查询地址", "TODO:填写地址");
        var summary = aionService.queryExchangePaidSummary(user);
        log.info("ExchangePaid 汇总: {}", summary);
    }

    // ===================== PaymentUsdtContract（只读）=====================

    /**
     * 方法作用：查询 PaymentUSDT 合约基础信息
     */
    @Test
    void testPaymentUsdtMeta() throws Exception {
        log.info("PaymentUSDT 地址: {}", paymentUsdtContract.getAddress());
        log.info("USDT 合约地址: {}", paymentUsdtContract.usdtAddress());
        log.info("管理员地址: {}", paymentUsdtContract.adminAddress());
        log.info("执行者地址: {}", paymentUsdtContract.executorAddress());
        log.info("收款地址: {}", paymentUsdtContract.treasuryAddress());
        log.info("最小扣款金额: {}", paymentUsdtContract.minAmount());
    }

    /**
     * 方法作用：查询订单号是否已执行
     */
    @Test
    void testPaymentUsdtExecuted() throws Exception {
        String orderIdHex = requireTodoString("PaymentUSDT 订单号", "TODO:填写订单ID(0x...)");
        log.info("订单执行状态: {}", paymentUsdtContract.executed(orderIdHex));
    }

    /**
     * 方法作用：查询授权额度（owner -> spender）
     */
    @Test
    void testPaymentUsdtAllowance() throws Exception {
        String owner = "0x75150a5dd1d992BEd89F49A15b6A70f43A5c8C64";
        String spender = "0x600F50058c8CEb5aD9448C82Ea3135D4C5539B12";
        log.info("授权额度: {}", paymentUsdtContract.allowance(owner, spender));
    }

    /**
     * 方法作用：查询授权额度（owner -> 合约）
     */
    @Test
    void testPaymentUsdtAllowanceToContract() throws Exception {
        String owner = "0x75150a5dd1d992BEd89F49A15b6A70f43A5c8C64";
        log.info("授权额度(合约): {}", paymentUsdtContract.allowanceToPaymentUsdt(owner));
    }

    /**
     * 方法作用：查询指定地址 USDT 余额
     */
    @Test
    void testPaymentUsdtBalanceOf() throws Exception {
        String owner = requireTodoString("PaymentUSDT balanceOf 地址", "TODO:填写地址");
        log.info("USDT 余额: {}", paymentUsdtContract.balanceOf(owner));
    }

    // ===================== PaymentUsdtContract（写操作）=====================

    /**
     * 方法作用：扣款测试
     */
    @Test
    void testPaymentUsdtDeposit() throws Exception {
        String user = requireTodoString("PaymentUSDT 扣款用户", "TODO:填写地址");
        BigDecimal amountHuman = requireTodoAmount("USDT 扣款金额", null);
        BigInteger amount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amountHuman);
        String orderIdHex = generateOrderIdHex();
        log.info("开始扣款，user: {}, amount(raw): {}, orderId: {}", user, amount, orderIdHex);
        var receipt = paymentUsdtContract.deposit(orderIdHex, user, amount);
        log.info("扣款结果: {}", receipt);
    }

    /**
     * 方法作用：设置收款地址
     */
    @Test
    void testPaymentUsdtSetTreasury() throws Exception {
        String newTreasury = requireTodoString("PaymentUSDT setTreasury 地址", "TODO:填写地址");
        var receipt = paymentUsdtContract.setTreasury(newTreasury);
        log.info("setTreasury 结果: {}", receipt);
    }

    /**
     * 方法作用：设置执行者地址
     */
    @Test
    void testPaymentUsdtSetExecutor() throws Exception {
        String newExecutor = requireTodoString("PaymentUSDT setExecutor 地址", "TODO:填写地址");
        var receipt = paymentUsdtContract.setExecutor(newExecutor);
        log.info("setExecutor 结果: {}", receipt);
    }

    /**
     * 方法作用：设置管理员地址
     */
    @Test
    void testPaymentUsdtSetAdmin() throws Exception {
        String newAdmin = requireTodoString("PaymentUSDT setAdmin 地址", "TODO:填写地址");
        var receipt = paymentUsdtContract.setAdmin(newAdmin);
        log.info("setAdmin 结果: {}", receipt);
    }

    /**
     * 方法作用：设置最小扣款金额
     */
    @Test
    void testPaymentUsdtSetMinAmount() throws Exception {
        BigInteger amount = new BigInteger(requireTodoString("PaymentUSDT setMinAmount", "TODO:填写最小金额（最小单位）"));
        var receipt = paymentUsdtContract.setMinAmount(amount);
        log.info("setMinAmount 结果: {}", receipt);
    }

    /**
     * 方法作用：暂停合约
     */
    @Test
    void testPaymentUsdtPause() throws Exception {
        var receipt = paymentUsdtContract.pause();
        log.info("pause 结果: {}", receipt);
    }

    /**
     * 方法作用：解除暂停
     */
    @Test
    void testPaymentUsdtUnpause() throws Exception {
        var receipt = paymentUsdtContract.unpause();
        log.info("unpause 结果: {}", receipt);
    }

    // ===================== CardNftContract（只读）=====================

    /**
     * 方法作用：查询铜卡ID
     */
    @Test
    void testCardNftCopperId() throws Exception {
        log.info("CardNFT COPPER_ID: {}", cardNftContract.copperId());
    }

    /**
     * 方法作用：查询银卡ID
     */
    @Test
    void testCardNftSilverId() throws Exception {
        log.info("CardNFT SILVER_ID: {}", cardNftContract.silverId());
    }

    /**
     * 方法作用：查询金卡ID
     */
    @Test
    void testCardNftGoldId() throws Exception {
        log.info("CardNFT GOLD_ID: {}", cardNftContract.goldId());
    }

    /**
     * 方法作用：查询名称
     */
    @Test
    void testCardNftName() throws Exception {
        log.info("CardNFT name: {}", cardNftContract.name());
    }

    /**
     * 方法作用：查询符号
     */
    @Test
    void testCardNftSymbol() throws Exception {
        log.info("CardNFT symbol: {}", cardNftContract.symbol());
    }

    /**
     * 方法作用：查询用户余额
     */
    @Test
    void testCardNftBalanceOf() throws Exception {
        String user = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        BigInteger cardId = cardNftContract.copperId();
        log.info("CardNFT balanceOf({}, {}): {}", user, cardId, cardNftContract.balanceOf(user, cardId));
    }

    /**
     * 方法作用：查询用户累计销毁数量
     */
    @Test
    void testCardNftBurnedAmount() throws Exception {
        String user = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        BigInteger cardId = cardNftContract.copperId();
        log.info("CardNFT burnedAmount({}, {}): {}", user, cardId, cardNftContract.burnedAmount(user, cardId));
    }

    /**
     * 方法作用：查询当前仍存在数量
     */
    @Test
    void testCardNftTotalSupply() throws Exception {
        BigInteger cardId = cardNftContract.copperId();
        log.info("CardNFT totalSupply({}): {}", cardId, cardNftContract.totalSupply(cardId));
    }

    /**
     * 方法作用：查询卡牌URI
     */
    @Test
    void testCardNftUri() throws Exception {
        BigInteger cardId = cardNftContract.copperId();
        log.info("CardNFT uri({}): {}", cardId, cardNftContract.uri(cardId));
    }

    /**
     * 方法作用：查询是否已授权管理员
     */
    @Test
    void testCardNftIsApprovedForAll() throws Exception {
        String user = requireTodoString("CardNFT isApprovedForAll user", "TODO:填写地址");
        String admin = requireTodoString("CardNFT isApprovedForAll admin", "TODO:填写地址");
        log.info("CardNFT isApprovedForAll({}, {}): {}", user, admin, cardNftContract.isApprovedForAll(user, admin));
    }

    /**
     * 方法作用：查询订单是否已使用
     */
    @Test
    void testCardNftOrderUsed() throws Exception {
        String orderId = requireTodoString("CardNFT orderUsed 订单号", "TODO:填写业务订单号（将自动keccak）");
        log.info("CardNFT orderUsed({}): {}", orderId, cardNftContract.isOrderUsed(orderId));
    }

    /**
     * 方法作用：查询订单详情
     */
    @Test
    void testCardNftGetOrder() throws Exception {
        String orderId = requireTodoString("CardNFT getOrder 订单号", "TODO:填写业务订单号（将自动keccak）");
        log.info("CardNFT getOrder({}): {}", orderId, cardNftContract.getOrder(orderId));
    }

    // ===================== CardNftContract（写操作）=====================

    /**
     * 方法作用：分发 NFT
     */
    @Test
    void testCardNftDistribute() throws Exception {
        String to = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        BigInteger cardId = cardNftContract.copperId();
        BigInteger amount = BigInteger.TEN;
        var receipt = cardNftContract.distribute(to, cardId, amount);
        log.info("distribute 结果: {}", receipt);
    }

    /**
     * 方法作用：管理员代用户销毁卡牌
     */
    @Test
    void testCardNftBurnWithOrder() throws Exception {
        String user = "0xa068802D54d2Aca1AD8cE6F2300eee02e3B50113";
        BigInteger cardId = cardNftContract.copperId();
        BigInteger amount = BigInteger.TWO;
        String orderId = requireTodoString("CardNFT burnWithOrder 订单号", "TODO:填写业务订单号（将自动keccak）");
        var receipt = cardNftContract.burnWithOrder(user, cardId, amount, orderId);
        log.info("burnWithOrder 结果: {}", receipt);
    }

    /**
     * 方法作用：设置管理员
     */
    @Test
    void testCardNftSetAdmin() throws Exception {
        String admin = requireTodoString("CardNFT setAdmin 地址", "TODO:填写地址");
        boolean enabled = true; // TODO: true/false
        var receipt = cardNftContract.setAdmin(admin, enabled);
        log.info("setAdmin 结果: {}", receipt);
    }

}
