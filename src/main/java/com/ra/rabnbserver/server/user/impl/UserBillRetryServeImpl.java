package com.ra.rabnbserver.server.user.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.enums.TransactionStatus;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.pojo.UserBill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@AbnormalRetryConfig(
        table = "user_bill",
        serviceName = "账本链上操作重试",
        idField = "id",
        userField = "user_id",
        statusField = "status",
        successValue = "1",
        failValue = "2",
        minIntervalSeconds = 10,
        timeoutSeconds = 300,
        maxRetryCount = 5,
        manualRemindIntervalSeconds = 10
)
public class UserBillRetryServeImpl extends AbstractAbnormalRetryService {

    private final UserBillMapper userBillMapper;
    private final PaymentUsdtContract paymentUsdtContract;
    private final CardNftContract cardNftContract;
    private static final Pattern QTY_PATTERN = Pattern.compile("x(\\d+)$");

    public UserBillRetryServeImpl(AbnormalRetryManager abnormalRetryManager,
                                  UserBillMapper userBillMapper,
                                  @Lazy PaymentUsdtContract paymentUsdtContract,
                                  @Lazy CardNftContract cardNftContract) {
        super(abnormalRetryManager);
        this.userBillMapper = userBillMapper;
        this.paymentUsdtContract = paymentUsdtContract;
        this.cardNftContract = cardNftContract;
    }

    @Override
    public boolean checkStatus(Long dataId) {
        UserBill bill = userBillMapper.selectById(dataId);
        if (bill == null) return false;

        // 1. 如果本地已经是成功，直接返回成功
        if (TransactionStatus.SUCCESS.equals(bill.getStatus())) {
            return true;
        }

        // 2. 根据不同业务类型去链上核实
        try {
            if (TransactionType.DEPOSIT.equals(bill.getTransactionType())) {
                // 充值业务：通过订单哈希在合约查询执行状态
                Boolean executed = paymentUsdtContract.executed(bill.getTransactionOrderId());
                if (Boolean.TRUE.equals(executed)) {
                    updateBillToSuccess(bill, null, "链上核实成功");
                    return true;
                }
            } else if (bill.getTxId() != null) {
                // 购买或奖励业务：如果有交易哈希，检查回执
//                Optional<TransactionReceipt> receipt = cardNftContract.getTransactionReceipt(bill.getTxId());
//                if (receipt.isPresent() && "0x1".equals(receipt.get().getStatus())) {
//                    updateBillToSuccess(bill, receipt.get(), "链上核实回执成功");
//                    return true;
//                }
                return false;
            }
        } catch (Exception e) {
            log.error("异常框架：检查账单 {} 链上状态失败: {}", dataId, e.getMessage());
        }
        return false;
    }

    @Override
    public boolean ExceptionHandling(Long dataId) {
        UserBill bill = userBillMapper.selectById(dataId);
        if (bill == null) return false;

        log.info("异常框架：正在执行自动重试，账单ID: {}, 类型: {}", dataId, bill.getTransactionType());

        try {
            // 充值操作：通常由用户签名，后端无法代为重试，只能核实，返回false触发持续提醒或等待checkStatus核实
            if (TransactionType.DEPOSIT.equals(bill.getTransactionType())) {
                return checkStatus(dataId);
            }

            // NFT购买/奖励分发：后端有Executor权限，可以发起自动重试补发
            if (TransactionType.PURCHASE.equals(bill.getTransactionType()) || TransactionType.REWARD.equals(bill.getTransactionType())) {
                int quantity = extractQuantity(bill.getRemark());
                if (quantity <= 0) {
                    log.error("异常框架：账单 {} 无法解析分发数量", dataId);
                    return false;
                }

                log.info("异常框架：重新发起NFT分发，用户: {}, 数量: {}", bill.getUserWalletAddress(), quantity);
                TransactionReceipt receipt = cardNftContract.distribute(bill.getUserWalletAddress(), BigInteger.valueOf(quantity));

                if (receipt != null && "0x1".equals(receipt.getStatus())) {
                    updateBillToSuccess(bill, receipt, "自动重试分发成功");
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("异常框架：重试执行过程中发生异常", e);
        }
        return false;
    }

    private void updateBillToSuccess(UserBill bill, TransactionReceipt receipt, String msg) {
        LambdaUpdateWrapper<UserBill> luw = new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, bill.getId())
                .set(UserBill::getStatus, TransactionStatus.SUCCESS)
                .set(UserBill::getRemark, bill.getRemark() + "(" + msg + ")");
        if (receipt != null) {
            luw.set(UserBill::getTxId, receipt.getTransactionHash())
                    .set(UserBill::getChainResponse, JSON.toJSONString(receipt));
        }
        userBillMapper.update(null, luw);
    }

    private int extractQuantity(String remark) {
        if (remark == null) return 0;
        Matcher matcher = QTY_PATTERN.matcher(remark.trim());
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    // 暴露基类方法供业务层调用
    @Override public void markAbnormal(Long dataId) { super.markAbnormal(dataId); }
    @Override public void markAbnormal(Long dataId, String userValue) { super.markAbnormal(dataId, userValue); }
    @Override public void checkUserErr(String userValue) { super.checkUserErr(userValue); }
    @Override public void ProcessingSuccessful(Long dataId) { super.ProcessingSuccessful(dataId); }
}