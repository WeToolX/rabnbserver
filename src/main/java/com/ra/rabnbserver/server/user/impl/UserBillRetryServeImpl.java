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

@Slf4j
@Service
@AbnormalRetryConfig(
        table = "user_bill",
        serviceName = "账本链上操作重试",
        idField = "id",
        userField = "user_wallet_address",
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

    public UserBillRetryServeImpl(AbnormalRetryManager abnormalRetryManager,
                                  UserBillMapper userBillMapper,
                                  @Lazy PaymentUsdtContract paymentUsdtContract,
                                  @Lazy CardNftContract cardNftContract) {
        super(abnormalRetryManager);
        this.userBillMapper = userBillMapper;
        this.paymentUsdtContract = paymentUsdtContract;
        this.cardNftContract = cardNftContract;
    }

    /**
     * 检查链上事务状态以判定本地账单是否应当标记成功
     */
    @Override
    public boolean checkStatus(Long dataId) {
        UserBill bill = userBillMapper.selectById(dataId);
        if (bill == null || TransactionStatus.SUCCESS.equals(bill.getStatus())) return true;

        try {
            // 针对充值业务通过订单号在合约中检索执行状态
            if (TransactionType.DEPOSIT.equals(bill.getTransactionType())) {
                if (Boolean.TRUE.equals(paymentUsdtContract.executed(bill.getTransactionOrderId()))) {
                    updateToSuccess(bill, null, "链上自动核实成功");
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("异常框架状态核查异常，账单ID：{}", dataId, e);
        }
        return false;
    }

    /**
     * 执行异常业务的自动重发逻辑
     */
    @Override
    public boolean ExceptionHandling(Long dataId) {
        UserBill bill = userBillMapper.selectById(dataId);
        if (bill == null) return false;

        try {
            // 充值业务无法由系统重发仅支持核实
            if (TransactionType.DEPOSIT.equals(bill.getTransactionType())) {
                return checkStatus(dataId);
            }

            // NFT分发类业务支持由后端执行器代为补发
            if (TransactionType.PURCHASE.equals(bill.getTransactionType()) || TransactionType.REWARD.equals(bill.getTransactionType())) {
                if (bill.getNum() == null || bill.getNum() <= 0) return false;
                if (bill.getCardId() == null) {
                    log.error("补发NFT失败：账单缺少卡牌ID，账单ID={}", dataId);
                    return false;
                }
                TransactionReceipt receipt = cardNftContract.distribute(
                        bill.getUserWalletAddress(),
                        BigInteger.valueOf(bill.getCardId()),
                        BigInteger.valueOf(bill.getNum())
                );
                if (receipt != null && "0x1".equals(receipt.getStatus())) {
                    updateToSuccess(bill, receipt, "重试机制补发成功 x" + bill.getNum());
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("重试执行异常，账单ID：{}", dataId, e);
        }
        return false;
    }

    /**
     * 内部辅助方法：完成账单状态更新
     */
    private void updateToSuccess(UserBill bill, TransactionReceipt receipt, String remark) {
        LambdaUpdateWrapper<UserBill> luw = new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, bill.getId())
                .set(UserBill::getStatus, TransactionStatus.SUCCESS)
                .set(UserBill::getRemark, remark);
        if (receipt != null) {
            luw.set(UserBill::getTxId, receipt.getTransactionHash())
                    .set(UserBill::getChainResponse, JSON.toJSONString(receipt));
        }
        userBillMapper.update(null, luw);
    }

    /**
     * 封装基类标记异常方法
     */
    @Override public void markAbnormal(Long dataId) { super.markAbnormal(dataId); }
    @Override public void markAbnormal(Long dataId, String userValue) { super.markAbnormal(dataId, userValue); }

    @Override
    public String manualSuccessRoute() {
        return "/api/admin/manual-bill-success";
    }
    @Override public void checkUserErr(String userValue) { super.checkUserErr(userValue); }
    @Override public void ProcessingSuccessful(Long dataId) { super.ProcessingSuccessful(dataId); }
}
