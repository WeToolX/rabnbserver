package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.AdminBillStatisticsVO;
import com.ra.rabnbserver.VO.PaymentUsdtMetaVO;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.contract.support.AmountConvertUtils;
import com.ra.rabnbserver.dto.AdminBillQueryDTO;
import com.ra.rabnbserver.dto.BillQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionStatus;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.card.EtfCardServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserBillServeImpl extends ServiceImpl<UserBillMapper, UserBill> implements UserBillServe {

    private final UserMapper userMapper;
    private final TransactionTemplate transactionTemplate;
    private final EtfCardServe etfCardServe;
    private final PaymentUsdtContract paymentUsdtContract;
    private final CardNftContract cardNftContract;

    private static final BigDecimal NFT_UNIT_PRICE = new BigDecimal("1");

    private final UserBillRetryServeImpl billRetryServe;



    /**
     * 统一创建账单并同步更新用户展示余额
     * @param userId  用户id
     * @param amount 操作金额
     * @param billType  账本类型
     * @param fundType  资金类型
     * @param txType 交易类型
     * @param remark 备注
     * @param orderId 订单id
     * @param txId   交易哈希
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBillAndUpdateBalance(Long userId, BigDecimal amount, BillType billType,
                                           FundType fundType, TransactionType txType,
                                           String remark, String orderId, String txId,String res) {
        if (StringUtils.isBlank(orderId)) {
            orderId = "BILL_" + IdWorker.getIdStr();
        }
        if (StringUtils.isBlank(remark)) {
            remark = txType.getDesc();
        }
        // 悲观锁锁定用户，确保流水计算的串行化
        // 即便不更新用户余额，锁定用户也是为了防止该用户的多条流水（同类型）并发插入导致余额计算错乱
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 动态获取该用户【当前账单类型】的上一笔最后余额
        // 逻辑：如果是平台账单查平台余额，如果是链上账单查链上余额
        BigDecimal balanceBefore = BigDecimal.ZERO;
        UserBill lastBill = this.getOne(new LambdaQueryWrapper<UserBill>()
                .eq(UserBill::getUserId, userId)
                .eq(UserBill::getBillType, billType)
                .orderByDesc(UserBill::getId)
                .last("LIMIT 1"));
        if (lastBill != null) {
            balanceBefore = lastBill.getBalanceAfter();
        }
        // 计算金额变动
        BigDecimal changeAmount = amount;
        if (FundType.EXPENSE.equals(fundType)) {
            changeAmount = amount.negate();
        }
        // 计算变动后的余额
        BigDecimal balanceAfter = balanceBefore.add(changeAmount);
        // 余额合法性校验（通常平台账单和链上账单都不允许余额小于0）
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("账户余额不足");
        }
        // 插入新账单
        UserBill newBill = new UserBill();
        newBill.setUserId(userId);
        newBill.setUserWalletAddress(user.getUserWalletAddress());
        newBill.setTransactionOrderId(orderId);
        newBill.setTxId(txId);
        newBill.setBillType(billType);
        newBill.setFundType(fundType);
        newBill.setTransactionType(txType);
        newBill.setAmount(amount);
        newBill.setBalanceBefore(balanceBefore);
        newBill.setBalanceAfter(balanceAfter);
        newBill.setRemark(remark);
        newBill.setTransactionTime(LocalDateTime.now());
        newBill.setStatus(TransactionStatus.SUCCESS);
        newBill.setChainResponse(res);
        this.save(newBill);
        // 同步更新用户表的余额字段（仅针对 PLATFORM 类型）
        if (BillType.PLATFORM.equals(billType)) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getBalance, balanceAfter));
        }
        log.info("账单记录成功: 类型={}, 用户={}, 变动前={}, 变动后={}",
                billType, userId, balanceBefore, balanceAfter);
    }

    @Override
    public IPage<UserBill> getUserBillPage(Long userId, BillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserBill::getUserId, userId);
        if (query.getBillType() != null) {
            wrapper.eq(UserBill::getBillType, query.getBillType());
        }
        if (query.getTransactionType() != null) {
            wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        }
        if (query.getFundType() != null) {
            wrapper.eq(UserBill::getFundType, query.getFundType());
        }
        if (query.getTransactionStatus() != null) {
            wrapper.eq(UserBill::getStatus, query.getTransactionStatus());
        }
        if (StringUtils.isNotBlank(query.getStartDate())) {
            LocalDateTime start = DateUtil.parse(query.getStartDate()).toLocalDateTime()
                    .with(LocalTime.MIN);
            wrapper.ge(UserBill::getTransactionTime, start);
        }
        if (StringUtils.isNotBlank(query.getEndDate())) {
            LocalDateTime end = DateUtil.parse(query.getEndDate()).toLocalDateTime()
                    .with(LocalTime.MAX);
            wrapper.le(UserBill::getTransactionTime, end);
        }
        wrapper.orderByDesc(UserBill::getTransactionTime)
                .orderByDesc(UserBill::getId);
        long current = (query.getPage() == null || query.getPage() < 1) ? 1 : query.getPage();
        long size = (query.getSize() == null || query.getSize() < 1) ? 10 : query.getSize();
        return this.page(new Page<>(current, size), wrapper);
    }




    /**
     * 充值接口链上扣款用户资金方法
     * @param userId
     * @param amount
     */
    @Override
    public void rechargeFromChain(Long userId, BigDecimal amount) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        // 框架检查用户是否有未处理的严重异常
        billRetryServe.checkUserErr(String.valueOf(userId));
        String orderId = "RECH_" + IdWorker.getIdStr();
        BigInteger rawAmount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amount);
        try {
            // 执行合约扣款
            log.info("发起链上充值扣款: 用户={}, 金额={}", user.getUserWalletAddress(), amount);
            TransactionReceipt receipt = paymentUsdtContract.deposit("0x" + IdWorker.get32UUID(), user.getUserWalletAddress(), rawAmount);
            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                // 合约执行成功 -> 创建成功账单 -> 标记框架成功
                transactionTemplate.execute(status -> {
                    createBillAndUpdateBalance(userId, amount, BillType.PLATFORM, FundType.INCOME,
                            TransactionType.DEPOSIT, "链上充值成功", orderId, receipt.getTransactionHash(), JSON.toJSONString(receipt));
                    return null;
                });
                UserBill bill = this.getOne(new LambdaQueryWrapper<UserBill>().eq(UserBill::getTransactionOrderId, orderId));
                if (bill != null) billRetryServe.ProcessingSuccessful(bill.getId());
                log.info("充值成功入账，账单ID: {}", bill.getId());
            } else {
                // 合约执行失败：创建失败账单 -> 标记异常框架
                Long billId = saveInitFailBill(user, amount, orderId, receipt != null ? receipt.getTransactionHash() : null, "合约返回失败");
                billRetryServe.markAbnormal(billId);
                throw new BusinessException("合约执行失败");
            }
        } catch (Exception e) {
            log.error("充值过程异常: ", e);
            Long billId = saveInitFailBill(user, amount, orderId, null, e.getMessage());
            billRetryServe.markAbnormal(billId);
            throw new BusinessException("充值异常: " + e.getMessage());
        }
    }

    @Override
    public void purchaseNftCard(Long userId, int quantity) {
        billRetryServe.checkUserErr(String.valueOf(userId));
        User user = userMapper.selectById(userId);
        ETFCard currentBatch = etfCardServe.getActiveAndEnabledBatch();
        if (currentBatch == null) throw new BusinessException("无可用批次");
        BigDecimal totalCost = currentBatch.getUnitPrice().multiply(new BigDecimal(quantity));
        String orderId = "NFT_BUY_" + IdWorker.getIdStr();
        // 事务内：先锁定库存和余额（防止并发超卖和余额双花）
        UserBill billRecord = transactionTemplate.execute(status -> {
            try {
                boolean invOk = etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                        .eq(ETFCard::getId, currentBatch.getId()).ge(ETFCard::getInventory, quantity)
                        .setSql("inventory = inventory - " + quantity).setSql("sold_count = sold_count + " + quantity));
                if (!invOk) throw new BusinessException("库存不足");
                createBillAndUpdateBalance(userId, totalCost, BillType.PLATFORM, FundType.EXPENSE,
                        TransactionType.PURCHASE, "购买NFT x" + quantity, orderId, null, null);
                return this.getOne(new LambdaQueryWrapper<UserBill>().eq(UserBill::getTransactionOrderId, orderId));
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
        // 事务外：调用合约分发（重要：即使合约超时，余额和库存也已扣除，避免回滚导致的逻辑错乱）
        try {
            log.info("开始NFT链上分发: 订单={}, 地址={}", orderId, user.getUserWalletAddress());
            TransactionReceipt receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(quantity));
            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                // 成功：补全账单状态 -> 标记框架成功
                this.update(new LambdaUpdateWrapper<UserBill>().eq(UserBill::getId, billRecord.getId())
                        .set(UserBill::getStatus, TransactionStatus.SUCCESS)
                        .set(UserBill::getTxId, receipt.getTransactionHash())
                        .set(UserBill::getChainResponse, JSON.toJSONString(receipt)));
                billRetryServe.ProcessingSuccessful(billRecord.getId());
            } else {
                // 合约显式失败：更新状态为 FAILED -> 标记框架异常
                markBillFailed(billRecord.getId(), receipt != null ? receipt.getTransactionHash() : null, "合约执行失败");
                billRetryServe.markAbnormal(billRecord.getId());
            }
        } catch (Exception e) {
            log.error("NFT分发合约异常: ", e);
            markBillFailed(billRecord.getId(), null, e.getMessage());
            billRetryServe.markAbnormal(billRecord.getId());
        }
    }


    private void markBillFailed(Long id, String txHash, String msg) {
        this.update(new LambdaUpdateWrapper<UserBill>().eq(UserBill::getId, id)
                .set(UserBill::getStatus, TransactionStatus.FAILED)
                .set(UserBill::getTxId, txHash)
                .set(UserBill::getRemark, "处理失败: " + msg));
    }

    private Long saveInitFailBill(User user, BigDecimal amount, String orderId, String txHash, String msg) {
        UserBill bill = new UserBill();
        bill.setUserId(user.getId());
        bill.setUserWalletAddress(user.getUserWalletAddress());
        bill.setTransactionOrderId(orderId);
        bill.setTxId(txHash);
        bill.setBillType(BillType.ERROR_ORDER);
        bill.setFundType(FundType.INCOME);
        bill.setTransactionType(TransactionType.DEPOSIT);
        bill.setAmount(amount);
        bill.setBalanceBefore(user.getBalance());
        bill.setBalanceAfter(user.getBalance());
        bill.setRemark("充值异常: " + msg);
        bill.setStatus(TransactionStatus.FAILED);
        bill.setTransactionTime(LocalDateTime.now());
        this.save(bill);
        return bill.getId();
    }

    @Override
    public IPage<UserBill> getAdminBillPage(AdminBillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getUserWalletAddress())) {
            wrapper.like(UserBill::getUserWalletAddress, query.getUserWalletAddress());
        }
        if (query.getBillType() != null) {
            wrapper.eq(UserBill::getBillType, query.getBillType());
        }
        if (query.getFundType() != null) {
            wrapper.eq(UserBill::getFundType, query.getFundType());
        }
        if (query.getTransactionType() != null) {
            wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(UserBill::getStatus, query.getStatus());
        }
        if (StringUtils.isNotBlank(query.getStartDate())) {
            LocalDateTime start = DateUtil.parse(query.getStartDate()).toLocalDateTime().with(LocalTime.MIN);
            wrapper.ge(UserBill::getTransactionTime, start);
        }
        if (StringUtils.isNotBlank(query.getEndDate())) {
            LocalDateTime end = DateUtil.parse(query.getEndDate()).toLocalDateTime().with(LocalTime.MAX);
            wrapper.le(UserBill::getTransactionTime, end);
        }
        wrapper.orderByDesc(UserBill::getTransactionTime).orderByDesc(UserBill::getId);
        Page<UserBill> pageParam = new Page<>(query.getPage(), query.getSize());
        return this.page(pageParam, wrapper);
    }

    @Override
    public AdminBillStatisticsVO getPlatformStatistics() {
        AdminBillStatisticsVO vo = new AdminBillStatisticsVO();
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User> userQuery = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        userQuery.select("SUM(balance) as sumBalance");
        List<Map<String, Object>> userMaps = userMapper.selectMaps(userQuery);
        if (!userMaps.isEmpty() && userMaps.get(0) != null && userMaps.get(0).get("sumBalance") != null) {
            vo.setTotalUserBalance(new BigDecimal(userMaps.get(0).get("sumBalance").toString()));
        }
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<UserBill> depositQuery = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        depositQuery.select("SUM(amount) as sumAmount")
                .eq("bill_type", BillType.PLATFORM.getCode())
                .eq("transaction_type", TransactionType.DEPOSIT.getCode())
                .eq("status", TransactionStatus.SUCCESS.getCode());
        List<java.util.Map<String, Object>> depositMaps = this.baseMapper.selectMaps(depositQuery);
        if (!depositMaps.isEmpty() && depositMaps.get(0) != null && depositMaps.get(0).get("sumAmount") != null) {
            vo.setTotalPlatformDeposit(new BigDecimal(depositMaps.get(0).get("sumAmount").toString()));
        }
        LambdaQueryWrapper<UserBill> purchaseWrapper = new LambdaQueryWrapper<>();
        purchaseWrapper.select(UserBill::getAmount, UserBill::getRemark)
                .eq(UserBill::getBillType, BillType.PLATFORM)
                .eq(UserBill::getTransactionType, TransactionType.PURCHASE)
                .eq(UserBill::getStatus, TransactionStatus.SUCCESS);
        List<UserBill> purchaseBills = this.list(purchaseWrapper);
        BigDecimal purchaseTotalAmount = BigDecimal.ZERO;
        int totalQuantity = 0;
        Pattern pattern = Pattern.compile("x(\\d+)$");
        for (UserBill bill : purchaseBills) {
            if (bill.getAmount() != null) {
                purchaseTotalAmount = purchaseTotalAmount.add(bill.getAmount());
            }
            String remark = bill.getRemark();
            if (StringUtils.isNotBlank(remark)) {
                Matcher matcher = pattern.matcher(remark.trim());
                if (matcher.find()) {
                    try {
                        String qStr = matcher.group(1); // 获取第一个括号捕获的内容
                        totalQuantity += Integer.parseInt(qStr);
                    } catch (NumberFormatException e) {
                        log.warn("账单ID: {} 备注数量解析失败: {}", bill.getId(), remark);
                    }
                }
            }
        }
        vo.setTotalNftPurchaseAmount(purchaseTotalAmount);
        vo.setTotalNftSalesCount(totalQuantity);
        return vo;
    }


    @Override
    public PaymentUsdtMetaVO getPaymentUsdtMeta() throws Exception {
        PaymentUsdtMetaVO vo = new PaymentUsdtMetaVO();
        vo.setContractAddress(paymentUsdtContract.getAddress());
        vo.setUsdtAddress(paymentUsdtContract.usdtAddress());
        vo.setAdminAddress(paymentUsdtContract.adminAddress());
        vo.setExecutorAddress(paymentUsdtContract.executorAddress());
        vo.setTreasuryAddress(paymentUsdtContract.treasuryAddress());
        // 获取最小扣款金额并转换精度
        BigInteger minRaw = paymentUsdtContract.minAmount();
        log.info("最小扣款金额 minRaw: {}", minRaw);
        BigDecimal humanAmount = new BigDecimal(minRaw)
                .divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP);
        vo.setMinAmount(humanAmount);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void distributeNftByAdmin(Long userId, Integer amount) {
        User user = userMapper.selectById(userId);
        if (user == null || StringUtils.isBlank(user.getUserWalletAddress())) {
            throw new BusinessException("用户不存在或未绑定钱包");
        }
        try {
            // 执行链上分发
            log.info("管理员手动分发NFT: 用户={}, 数量={}", user.getUserWalletAddress(), amount);
            TransactionReceipt receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(amount));
            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                // 记录账单（类型为奖励，不扣除余额，仅做记录）
                this.createBillAndUpdateBalance(
                        userId,
                        BigDecimal.ZERO,
                        BillType.ON_CHAIN,
                        FundType.INCOME,
                        TransactionType.REWARD,
                        "系统管理员手动分发NFT x" + amount,
                        "DIST_" + IdWorker.getIdStr(),
                        receipt.getTransactionHash(),
                        JSON.toJSONString(receipt)
                );
            } else {
                throw new BusinessException("链上分发失败");
            }
        } catch (Exception e) {
            log.error("手动分发NFT异常", e);
            throw new BusinessException("系统分发NTF失败: " + e.getMessage());
        }
    }
}
