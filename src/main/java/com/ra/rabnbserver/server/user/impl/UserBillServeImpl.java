package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.AdminBillStatisticsVO;
import com.ra.rabnbserver.VO.PaymentUsdtMetaVO;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.contract.support.AmountConvertUtils;
import com.ra.rabnbserver.dto.AdminBillQueryDTO;
import com.ra.rabnbserver.dto.BillQueryDTO;
import com.ra.rabnbserver.enums.*;
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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserBillServeImpl extends ServiceImpl<UserBillMapper, UserBill> implements UserBillServe {

    private final UserMapper userMapper;
    private final TransactionTemplate transactionTemplate;
    private final EtfCardServe etfCardServe;
    private final PaymentUsdtContract paymentUsdtContract;
    private final CardNftContract cardNftContract;
    private final UserBillRetryServeImpl billRetryServe;

    @Override
    public void rechargeFromChain(Long userId, BigDecimal amount) throws Exception {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        billRetryServe.checkUserErr(String.valueOf(userId));
        String orderIdHex = "0x" + com.baomidou.mybatisplus.core.toolkit.IdWorker.get32UUID();
        BigInteger rawAmount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amount);

        // 预检查链上状态（授权与余额）
        BigInteger allowance = paymentUsdtContract.allowanceToPaymentUsdt(user.getUserWalletAddress());
        if (allowance.compareTo(rawAmount) < 0) throw new BusinessException("用户授权额度不足");
        BigInteger balance = paymentUsdtContract.balanceOf(user.getUserWalletAddress());
        if (balance.compareTo(rawAmount) < 0) throw new BusinessException("用户链上USDT余额不足");

        // 1. 先保存一条失败/待处理的账单，获取ID，进入异常框架视野
        Long billId = saveInitChainBill(user, amount, orderIdHex, TransactionType.DEPOSIT, BillType.ERROR_ORDER, "USDT充值发起中");

        try {
            log.info("发起链上充值扣款: 用户={}, 订单={}", user.getUserWalletAddress(), orderIdHex);
            TransactionReceipt receipt = paymentUsdtContract.deposit(orderIdHex, user.getUserWalletAddress(), rawAmount);

            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                // 成功：更新账单并标记框架处理成功
                completeChainBill(billId, receipt, "USDT充值成功");
                billRetryServe.ProcessingSuccessful(billId);
            } else {
                updateBillRemark(billId, receipt != null ? receipt.getTransactionHash() : null, "链上合约执行失败");
                billRetryServe.markAbnormal(billId, String.valueOf(userId));
                throw new BusinessException("合约执行失败，系统已记录，请勿重复操作");
            }
        } catch (Exception e) {
            log.error("充值异常: ", e);
            updateBillRemark(billId, null, "系统请求异常: " + e.getMessage());
            billRetryServe.markAbnormal(billId, String.valueOf(userId));
            throw new BusinessException("充值指令已提交，链上处理中，请稍后查看");
        }
    }

    @Override
    public void purchaseNftCard(Long userId, int quantity) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        billRetryServe.checkUserErr(String.valueOf(userId));

        ETFCard currentBatch = etfCardServe.getActiveAndEnabledBatch();
        if (currentBatch == null) throw new BusinessException("当前无可用卡牌批次");
        BigDecimal totalCost = currentBatch.getUnitPrice().multiply(new BigDecimal(quantity));
        String orderId = "NFT_BUY_" + com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr();

        // 1. 事务：扣除库存和余额，创建初始账单（设为FAILED状态，等待链上结果）
        UserBill billRecord = transactionTemplate.execute(status -> {
            try {
                etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                        .eq(ETFCard::getId, currentBatch.getId()).ge(ETFCard::getInventory, quantity)
                        .setSql("inventory = inventory - " + quantity).setSql("sold_count = sold_count + " + quantity));

                // 扣除用户余额并生成记录
                createBillAndUpdateBalance(userId, totalCost, BillType.PLATFORM, FundType.EXPENSE,
                        TransactionType.PURCHASE, "购买NFT卡牌 x" + quantity, orderId, null, null);

                UserBill bill = this.getOne(new LambdaQueryWrapper<UserBill>().eq(UserBill::getTransactionOrderId, orderId));
                // 将刚生成的账单状态暂时置为 FAILED，直到链上成功
                this.update(new LambdaUpdateWrapper<UserBill>().eq(UserBill::getId, bill.getId()).set(UserBill::getStatus, TransactionStatus.FAILED));
                return bill;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });

        // 2. 发起链上分发
        try {
            log.info("发起NFT购买分发: 地址={}, 数量={}", user.getUserWalletAddress(), quantity);
            TransactionReceipt receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(quantity));

            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                completeChainBill(billRecord.getId(), receipt, "购买NFT卡牌成功 x" + quantity);
                billRetryServe.ProcessingSuccessful(billRecord.getId());
            } else {
                updateBillRemark(billRecord.getId(), receipt != null ? receipt.getTransactionHash() : null, "分发合约执行失败");
                billRetryServe.markAbnormal(billRecord.getId(), String.valueOf(userId));
            }
        } catch (Exception e) {
            log.error("NFT分发异常: ", e);
            updateBillRemark(billRecord.getId(), null, "分发请求异常: " + e.getMessage());
            billRetryServe.markAbnormal(billRecord.getId(), String.valueOf(userId));
            throw new BusinessException(0, "卡牌分发处理中，请勿重复购买");
        }
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

    @Override
    public void distributeNftByAdmin(Long userId, Integer amount) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");

        String orderId = "DIST_" + com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr();
        Long billId = saveInitChainBill(user, BigDecimal.ZERO, orderId, TransactionType.REWARD, BillType.ON_CHAIN, "管理员分发NFT x" + amount);

        try {
            log.info("管理员分发NFT: 用户={}, 数量={}", user.getUserWalletAddress(), amount);
            TransactionReceipt receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(amount));

            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                completeChainBill(billId, receipt, "系统管理员手动分发NFT x" + amount);
                //billRetryServe.ProcessingSuccessful(billId);
            } else {
                updateBillRemark(billId, receipt != null ? receipt.getTransactionHash() : null, "管理员分发合约失败");
                billRetryServe.markAbnormal(billId, String.valueOf(userId));
                throw new BusinessException("链上分发失败");
            }
        } catch (Exception e) {
            log.error("管理员分发异常: ", e);
            updateBillRemark(billId, null, "分发异常: " + e.getMessage());
            billRetryServe.markAbnormal(billId, String.valueOf(userId));
            throw new BusinessException("分发已记录并转入异常处理队列: " + e.getMessage());
        }
    }

    // --- 私有辅助方法 ---

    private Long saveInitChainBill(User user, BigDecimal amount, String orderId, TransactionType tType, BillType bType, String remark) {
        UserBill bill = new UserBill();
        bill.setUserId(user.getId());
        bill.setUserWalletAddress(user.getUserWalletAddress());
        bill.setTransactionOrderId(orderId);
        bill.setBillType(bType);
        bill.setFundType(FundType.INCOME);
        bill.setTransactionType(tType);
        bill.setAmount(amount);
        bill.setBalanceBefore(user.getBalance());
        bill.setBalanceAfter(user.getBalance());
        bill.setRemark(remark);
        bill.setStatus(TransactionStatus.FAILED);
        bill.setTransactionTime(LocalDateTime.now());
        log.info("saveInitChainBill,写入数据:{}", bill);
        this.save(bill);
        return bill.getId();
    }

    private void completeChainBill(Long billId, TransactionReceipt receipt, String remark) {
        this.update(new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, billId)
                .set(UserBill::getStatus, TransactionStatus.SUCCESS)
                .set(UserBill::getTxId, receipt.getTransactionHash())
                .set(UserBill::getChainResponse, JSON.toJSONString(receipt))
                .set(UserBill::getRemark, remark));
    }

    private void updateBillRemark(Long billId, String txHash, String errorMsg) {
        this.update(new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, billId)
                .set(UserBill::getTxId, txHash)
                .set(UserBill::getRemark, "链上处理异常，原因: " + errorMsg));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBillAndUpdateBalance(Long userId, BigDecimal amount, BillType billType,
                                           FundType fundType, TransactionType txType,
                                           String remark, String orderId, String txId, String res) {
        // ... 此处保留原逻辑不变 ...
        if (StringUtils.isBlank(orderId)) {
            orderId = "BILL_" + com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr();
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId).last("FOR UPDATE"));
        if (user == null) throw new BusinessException("用户不存在");

        BigDecimal balanceBefore = BigDecimal.ZERO;
        UserBill lastBill = this.getOne(new LambdaQueryWrapper<UserBill>()
                .eq(UserBill::getUserId, userId)
                .eq(UserBill::getBillType, billType)
                .orderByDesc(UserBill::getId)
                .last("LIMIT 1"));
        if (lastBill != null) balanceBefore = lastBill.getBalanceAfter();

        BigDecimal changeAmount = FundType.EXPENSE.equals(fundType) ? amount.negate() : amount;
        BigDecimal balanceAfter = balanceBefore.add(changeAmount);

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("账户余额不足");

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

        if (BillType.PLATFORM.equals(billType)) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getBalance, balanceAfter));
        }
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


    // ... 其他分页和统计方法保留 ...
}