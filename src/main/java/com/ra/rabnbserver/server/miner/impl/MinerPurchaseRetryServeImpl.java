package com.ra.rabnbserver.server.miner.impl;

import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.pojo.UserMiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

@Slf4j
@Service
@AbnormalRetryConfig(
        table = "user_miner",
        serviceName = "矿机购买卡牌销毁",
        idField = "id",
        userField = "walletAddress",
        statusField = "nft_burn_status", // 监控这个业务状态
        successValue = "1",
        failValue = "0",
        minIntervalSeconds = 60,
        timeoutSeconds = 600,
        maxRetryCount = 10,
        manualRemindIntervalSeconds = 10
)
public class MinerPurchaseRetryServeImpl extends AbstractAbnormalRetryService {

    private final UserMinerMapper userMinerMapper;
    private final CardNftContract cardNftContract;

    public MinerPurchaseRetryServeImpl(AbnormalRetryManager abnormalRetryManager, UserMinerMapper userMinerMapper, CardNftContract cardNftContract) {
        super(abnormalRetryManager);
        this.userMinerMapper = userMinerMapper;
        this.cardNftContract = cardNftContract;
    }

    @Override
    public void markAbnormal(Long dataId) {
        super.markAbnormal(dataId);
    }

    @Override
    public void markAbnormal(Long dataId, String userValue) {
        super.markAbnormal(dataId, userValue);
    }

    @Override
    public String manualSuccessRoute() {
        return "/api/admin/miner/manual-purchase-success";
    }

    @Override
    public void checkUserErr(String userValue) {
        super.checkUserErr(userValue);
    }

    @Override
    public void ProcessingSuccessful(Long dataId) {
        super.ProcessingSuccessful(dataId);
    }

    @Override
    public boolean checkStatus(Long dataId) {
        UserMiner miner = userMinerMapper.selectById(dataId);
        return miner != null && Integer.valueOf(1).equals(miner.getStatus());
    }

    @Override
    public boolean ExceptionHandling(Long dataId) {
        UserMiner miner = userMinerMapper.selectById(dataId);
        if (miner == null) return false;

        try {
            // 1. 检查授权
//            String admin = cardNftContract.getAddress();
//            if (!cardNftContract.isApprovedForAll(miner.getWalletAddress(), admin)) {
//                log.warn("用户 {} 未授权卡牌合约", miner.getWalletAddress());
//                return false;
//            }

            // 2. 调用合约销毁 (销毁数量1)
            TransactionReceipt receipt = cardNftContract.burnUser(miner.getWalletAddress(), BigInteger.ONE);
            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                miner.setNftBurnStatus(1);
                userMinerMapper.updateById(miner);
                return true;
            }
        } catch (Exception e) {
            log.error("销毁重试异常: {}", e.getMessage());
        }
        return false;
    }
}
