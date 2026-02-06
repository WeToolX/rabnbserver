package com.ra.rabnbserver.server.miner.impl;

import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.pojo.UserMiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AbnormalRetryConfig(
        table = "user_miner",
        serviceName = "矿机购买卡牌销毁",
        idField = "id",
        userField = "walletAddress",
        statusField = "status",
        successValue = "1",
        failValue = "0",
        minIntervalSeconds = 60,
        timeoutSeconds = 600,
        maxRetryCount = 10,
        manualRemindIntervalSeconds = 10
)
public class MinerPurchaseRetryServeImpl extends AbstractAbnormalRetryService {

    private final UserMinerMapper userMinerMapper;

    public MinerPurchaseRetryServeImpl(AbnormalRetryManager abnormalRetryManager, UserMinerMapper userMinerMapper) {
        super(abnormalRetryManager);
        this.userMinerMapper = userMinerMapper;
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
        try {
            log.info("重试框架：正在尝试重新销毁卡牌并激活矿机，ID: {}", dataId);
            // 模拟调用合约销毁卡牌
            boolean contractSuccess = true;
            if (contractSuccess) {
                UserMiner miner = userMinerMapper.selectById(dataId);
                miner.setStatus(1);
                userMinerMapper.updateById(miner);
                return true;
            }
        } catch (Exception e) {
            log.error("重试框架：矿机激活重试异常，ID: {}, 原因: {}", dataId, e.getMessage());
        }
        return false;
    }
}
