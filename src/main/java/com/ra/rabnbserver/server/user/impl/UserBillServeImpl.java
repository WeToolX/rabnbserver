package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    /**
     * 链上调用执行接口用于封装合约交互逻辑
     */
    @FunctionalInterface
    interface ChainCall {
        TransactionReceipt execute() throws Exception;
    }

    /**
     * 处理链上充值业务
     */
    @Override
    public void rechargeFromChain(Long userId, BigDecimal amount) throws Exception {
        User user = validateUserAndCheckLock(userId);
        String orderId = "0x" + IdWorker.get32UUID();
        BigInteger rawAmount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amount);

        // 验证链上授权额度与余额
        checkChainAllowanceAndBalance(user.getUserWalletAddress(), rawAmount);

        try {
            log.info("发起链上充值请求，用户：{}，金额：{}", user.getUserWalletAddress(), amount);
            TransactionReceipt receipt = paymentUsdtContract.deposit(orderId, user.getUserWalletAddress(), rawAmount);

            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                // 充值成功后统一调用创建账单方法增加用户余额
                createBillAndUpdateBalance(userId, amount, BillType.PLATFORM, FundType.INCOME,
                        TransactionType.DEPOSIT, "USDT充值成功", orderId, receipt.getTransactionHash(), JSON.toJSONString(receipt), 0);
                billRetryServe.ProcessingSuccessful(getBillIdByOrder(orderId));
            } else {
                // 合约执行失败时记录一笔零元账单用于异常框架追踪
                createBillAndUpdateBalance(userId, BigDecimal.ZERO, BillType.ERROR_ORDER, FundType.INCOME,
                        TransactionType.DEPOSIT, "链上合约执行失败", orderId, receipt != null ? receipt.getTransactionHash() : null, null, 0);
                Long billId = getBillIdByOrder(orderId);
                updateBillStatus(billId, TransactionStatus.FAILED, "合约执行失败");
                billRetryServe.markAbnormal(billId, String.valueOf(userId));
                throw new BusinessException("合约执行失败，系统已记录");
            }
        } catch (Exception e) {
            log.error("充值过程发生异常", e);
            // 发生异常时记录零元账单并进入异常处理队列
            createBillAndUpdateBalance(userId, BigDecimal.ZERO, BillType.ERROR_ORDER, FundType.INCOME,
                    TransactionType.DEPOSIT, "系统异常：" + e.getMessage(), orderId, null, null, 0);
            Long billId = getBillIdByOrder(orderId);
            updateBillStatus(billId, TransactionStatus.FAILED, "充值发起异常");
            billRetryServe.markAbnormal(billId, String.valueOf(userId));
            throw new BusinessException("充值指令已提交，请稍后在账单中查看结果");
        }
    }

    /**
     * 处理购买NFT卡牌业务
     */
    @Override
    public void purchaseNftCard(Long userId, int quantity) {
        User user = validateUserAndCheckLock(userId);
        ETFCard currentBatch = etfCardServe.getActiveAndEnabledBatch();
        if (currentBatch == null) {
            throw new BusinessException("当前无可用卡牌批次");
        }

        // 获取链上剩余库存并同步数据库
        BigInteger remaining = syncChainInventory(currentBatch.getId());
        if (remaining.compareTo(BigInteger.valueOf(quantity)) < 0) {
            throw new BusinessException("链上库存不足，剩余数量：" + remaining);
        }

        BigDecimal totalCost = currentBatch.getUnitPrice().multiply(new BigDecimal(quantity));
        String orderId = "NFT_BUY_" + IdWorker.getIdStr();

        // 事务内执行库存扣减与余额扣除
        transactionTemplate.executeWithoutResult(status -> {
            try {
                boolean stockOk = etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                        .eq(ETFCard::getId, currentBatch.getId())
                        .ge(ETFCard::getInventory, quantity)
                        .setSql("inventory = inventory - " + quantity)
                        .setSql("sold_count = sold_count + " + quantity));
                if (!stockOk) throw new BusinessException("本地库存不足");

                // 使用统一方法扣除余额并生成初始成功的账单
                createBillAndUpdateBalance(userId, totalCost, BillType.PLATFORM, FundType.EXPENSE,
                        TransactionType.PURCHASE, "购买NFT卡牌 x" + quantity, orderId, null, null, quantity);
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });

        Long billId = getBillIdByOrder(orderId);

        try {
            log.info("发起NFT链上分发，用户：{}，数量：{}", user.getUserWalletAddress(), quantity);
            TransactionReceipt receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(quantity));

            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                updateBillReceipt(billId, receipt, "购买NFT卡牌成功 x" + quantity);
                billRetryServe.ProcessingSuccessful(billId);
            } else {
                // 链上失败则修改账单状态为失败并进入异常框架
                updateBillStatus(billId, TransactionStatus.FAILED, "分发合约执行失败");
                billRetryServe.markAbnormal(billId, String.valueOf(userId));
            }
        } catch (Exception e) {
            log.error("NFT分发执行异常", e);
            updateBillStatus(billId, TransactionStatus.FAILED, "分发请求异常：" + e.getMessage());
            billRetryServe.markAbnormal(billId, String.valueOf(userId));
            throw new BusinessException(0, "卡牌分发处理中，请稍后查看");
        }
    }

    /**
     * 处理管理员分发NFT业务
     */
    @Override
    public void distributeNftByAdmin(Long userId, Integer amount) {
        User user = validateUserAndCheckLock(userId);
        String orderId = "DIST_" + IdWorker.getIdStr();

        // 管理员分发不涉及金额变动，调用统一方法创建一笔零元账单
        createBillAndUpdateBalance(userId, BigDecimal.ZERO, BillType.ON_CHAIN, FundType.INCOME,
                TransactionType.REWARD, "管理员发起分发 x" + amount, orderId, null, null, amount);

        Long billId = getBillIdByOrder(orderId);

        try {
            TransactionReceipt receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(amount));
            if (receipt != null && "0x1".equals(receipt.getStatus())) {
                updateBillReceipt(billId, receipt, "系统管理员手动分发成功 x" + amount);
            } else {
                updateBillStatus(billId, TransactionStatus.FAILED, "管理员分发链上失败");
                billRetryServe.markAbnormal(billId, String.valueOf(userId));
                throw new BusinessException("链上分发失败");
            }
        } catch (Exception e) {
            log.error("管理员分发异常", e);
            updateBillStatus(billId, TransactionStatus.FAILED, "分发异常：" + e.getMessage());
            billRetryServe.markAbnormal(billId, String.valueOf(userId));
            throw new BusinessException("请求已提交至异常处理队列");
        }
    }

    /**
     * 验证用户状态并检查异常处理锁定
     */
    private User validateUserAndCheckLock(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        billRetryServe.checkUserErr(String.valueOf(userId));
        return user;
    }

    /**
     * 同步链上库存至本地数据库
     */
    private BigInteger syncChainInventory(Long batchId) {
        try {
            BigInteger remaining = cardNftContract.remainingMintable();
            etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                    .eq(ETFCard::getId, batchId)
                    .set(ETFCard::getInventory, remaining.intValue()));
            return remaining;
        } catch (Exception e) {
            log.error("链上库存获取失败", e);
            throw new BusinessException("获取链上库存失败");
        }
    }

    /**
     * 根据订单号获取账单唯一标识
     */
    private Long getBillIdByOrder(String orderId) {
        UserBill bill = this.getOne(new LambdaQueryWrapper<UserBill>().eq(UserBill::getTransactionOrderId, orderId));
        return bill != null ? bill.getId() : null;
    }

    /**
     * 更新账单状态及备注内容
     */
    private void updateBillStatus(Long billId, TransactionStatus status, String remark) {
        this.update(new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, billId)
                .set(UserBill::getStatus, status)
                .set(UserBill::getRemark, remark));
    }

    /**
     * 更新账单的链上回执信息
     */
    private void updateBillReceipt(Long billId, TransactionReceipt receipt, String remark) {
        this.update(new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, billId)
                .set(UserBill::getStatus, TransactionStatus.SUCCESS)
                .set(UserBill::getTxId, receipt.getTransactionHash())
                .set(UserBill::getChainResponse, JSON.toJSONString(receipt))
                .set(UserBill::getRemark, remark));
    }

    /**
     * 校验链上资产状况
     */
    private void checkChainAllowanceAndBalance(String address, BigInteger amount) throws Exception {
        if (paymentUsdtContract.allowanceToPaymentUsdt(address).compareTo(amount) < 0) throw new BusinessException("授权额度不足");
        if (paymentUsdtContract.balanceOf(address).compareTo(amount) < 0) throw new BusinessException("链上余额不足");
    }

    /**
     * 统一账单创建与余额变动逻辑方法
     */

    @Override
    public void createBillAndUpdateBalance(Long userId, BigDecimal amount, BillType billType, FundType fundType, TransactionType txType, String remark, String orderId, String txId, String res, int num) {
        if (StringUtils.isBlank(orderId)) orderId = "BILL_" + IdWorker.getIdStr();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getId, userId).last("FOR UPDATE"));
        if (user == null) throw new BusinessException("用户不存在");

        BigDecimal balanceBefore = user.getBalance();
        BigDecimal change = FundType.EXPENSE.equals(fundType) ? amount.negate() : amount;
        BigDecimal balanceAfter = balanceBefore.add(change);

        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException("账户余额不足");

        UserBill bill = new UserBill();
        bill.setUserId(userId);
        bill.setUserWalletAddress(user.getUserWalletAddress());
        bill.setTransactionOrderId(orderId);
        bill.setTxId(txId);
        bill.setBillType(billType);
        bill.setFundType(fundType);
        bill.setTransactionType(txType);
        bill.setAmount(amount);
        bill.setNum(num);
        bill.setBalanceBefore(balanceBefore);
        bill.setBalanceAfter(balanceAfter);
        bill.setRemark(remark);
        bill.setTransactionTime(LocalDateTime.now());
        bill.setStatus(TransactionStatus.SUCCESS);
        bill.setChainResponse(res);
        this.save(bill);

        if (BillType.PLATFORM.equals(billType) && amount.compareTo(BigDecimal.ZERO) != 0) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId).set(User::getBalance, balanceAfter));
        }
    }

    /**
     * 管理端账单分页查询
     */
    @Override
    public IPage<UserBill> getAdminBillPage(AdminBillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getUserWalletAddress())) wrapper.like(UserBill::getUserWalletAddress, query.getUserWalletAddress());
        if (query.getBillType() != null) wrapper.eq(UserBill::getBillType, query.getBillType());
        if (query.getFundType() != null) wrapper.eq(UserBill::getFundType, query.getFundType());
        if (query.getTransactionType() != null) wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        if (query.getStatus() != null) wrapper.eq(UserBill::getStatus, query.getStatus());
        if (StringUtils.isNotBlank(query.getStartDate())) wrapper.ge(UserBill::getTransactionTime, DateUtil.parse(query.getStartDate()).toLocalDateTime().with(LocalTime.MIN));
        if (StringUtils.isNotBlank(query.getEndDate())) wrapper.le(UserBill::getTransactionTime, DateUtil.parse(query.getEndDate()).toLocalDateTime().with(LocalTime.MAX));
        wrapper.orderByDesc(UserBill::getTransactionTime).orderByDesc(UserBill::getId);
        return this.page(new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    /**
     * 平台全局统计数据获取
     */
    @Override
    public AdminBillStatisticsVO getPlatformStatistics() {
        AdminBillStatisticsVO vo = new AdminBillStatisticsVO();
        QueryWrapper<User> uq = new QueryWrapper<>();
        uq.select("SUM(balance) as sb");
        Map<String, Object> um = userMapper.selectMaps(uq).get(0);
        if (um != null && um.get("sb") != null) vo.setTotalUserBalance(new BigDecimal(um.get("sb").toString()));

        QueryWrapper<UserBill> dq = new QueryWrapper<>();
        dq.select("SUM(amount) as sa").eq("bill_type", BillType.PLATFORM.getCode()).eq("transaction_type", TransactionType.DEPOSIT.getCode()).eq("status", TransactionStatus.SUCCESS.getCode());
        Map<String, Object> dm = this.baseMapper.selectMaps(dq).get(0);
        if (dm != null && dm.get("sa") != null) vo.setTotalPlatformDeposit(new BigDecimal(dm.get("sa").toString()));

        List<UserBill> bills = this.list(new LambdaQueryWrapper<UserBill>().select(UserBill::getAmount, UserBill::getRemark).eq(UserBill::getBillType, BillType.PLATFORM).eq(UserBill::getTransactionType, TransactionType.PURCHASE).eq(UserBill::getStatus, TransactionStatus.SUCCESS));
        BigDecimal totalAmt = BigDecimal.ZERO;
        int totalQty = 0;
        Pattern p = Pattern.compile("x(\\d+)$");
        for (UserBill b : bills) {
            if (b.getAmount() != null) totalAmt = totalAmt.add(b.getAmount());
            if (StringUtils.isNotBlank(b.getRemark())) {
                Matcher m = p.matcher(b.getRemark().trim());
                if (m.find()) totalQty += Integer.parseInt(m.group(1));
            }
        }
        vo.setTotalNftPurchaseAmount(totalAmt);
        vo.setTotalNftSalesCount(totalQty);
        return vo;
    }

    /**
     * 获取USDT支付配置元数据
     */
    @Override
    public PaymentUsdtMetaVO getPaymentUsdtMeta() throws Exception {
        PaymentUsdtMetaVO vo = new PaymentUsdtMetaVO();
        vo.setContractAddress(paymentUsdtContract.getAddress());
        vo.setUsdtAddress(paymentUsdtContract.usdtAddress());
        vo.setAdminAddress(paymentUsdtContract.adminAddress());
        vo.setExecutorAddress(paymentUsdtContract.executorAddress());
        vo.setTreasuryAddress(paymentUsdtContract.treasuryAddress());
        vo.setMinAmount(new BigDecimal(paymentUsdtContract.minAmount()).divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP));
        return vo;
    }

    /**
     * 用户个人账单分页查询
     */
    @Override
    public IPage<UserBill> getUserBillPage(Long userId, BillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<UserBill>().eq(UserBill::getUserId, userId);
        if (query.getBillType() != null) wrapper.eq(UserBill::getBillType, query.getBillType());
        if (query.getTransactionType() != null) wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        if (query.getFundType() != null) wrapper.eq(UserBill::getFundType, query.getFundType());
        if (query.getTransactionStatus() != null) wrapper.eq(UserBill::getStatus, query.getTransactionStatus());
        if (StringUtils.isNotBlank(query.getStartDate())) wrapper.ge(UserBill::getTransactionTime, DateUtil.parse(query.getStartDate()).toLocalDateTime().with(LocalTime.MIN));
        if (StringUtils.isNotBlank(query.getEndDate())) wrapper.le(UserBill::getTransactionTime, DateUtil.parse(query.getEndDate()).toLocalDateTime().with(LocalTime.MAX));
        wrapper.orderByDesc(UserBill::getTransactionTime).orderByDesc(UserBill::getId);
        return this.page(new Page<>(query.getPage() == null ? 1 : query.getPage(), query.getSize() == null ? 10 : query.getSize()), wrapper);
    }
}