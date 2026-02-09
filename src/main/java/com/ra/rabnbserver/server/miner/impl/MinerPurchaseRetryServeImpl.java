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
        // 核心修正：必须检查 nftBurnStatus 是否已经是 1
        return miner != null && Integer.valueOf(1).equals(miner.getNftBurnStatus());
    }

    @Override
    public boolean ExceptionHandling(Long dataId) {
        UserMiner miner = userMinerMapper.selectById(dataId);
        if (miner == null) return false;
        try {
            TransactionReceipt receipt = cardNftContract.burnUser(miner.getWalletAddress(), BigInteger.ONE);
            // ContractBase 及其子类在 status=0x0 时会抛出 ContractCallException
            // 此处逻辑只需判断 receipt 是否成功到达
            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                miner.setNftBurnStatus(1);
                userMinerMapper.updateById(miner);
                log.info("矿机ID: {} 链上补录销毁成功", dataId);
                return true;
            }
        } catch (Exception e) {
            log.error("销毁重试执行异常, ID: {}, 原因: {}", dataId, e.getMessage());
        }
        return false;
    }
}
