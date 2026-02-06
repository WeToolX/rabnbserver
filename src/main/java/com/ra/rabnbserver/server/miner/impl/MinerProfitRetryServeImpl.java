package com.ra.rabnbserver.server.miner.impl;

import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.mapper.MinerProfitRecordMapper;
import com.ra.rabnbserver.pojo.MinerProfitRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AbnormalRetryConfig(
        table = "miner_profit_record",
        serviceName = "每日收益合约发放",
        idField = "id",
        userField = "walletAddress",
        statusField = "status",
        successValue = "1",
        failValue = "0",
        minIntervalSeconds = 120,
        timeoutSeconds = 1800,
        maxRetryCount = 5,
        manualRemindIntervalSeconds = 10
)
public class MinerProfitRetryServeImpl extends AbstractAbnormalRetryService {

    private final MinerProfitRecordMapper profitRecordMapper;

    public MinerProfitRetryServeImpl(AbnormalRetryManager abnormalRetryManager, MinerProfitRecordMapper profitRecordMapper) {
        super(abnormalRetryManager);
        this.profitRecordMapper = profitRecordMapper;
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
        return "/user/miner/manual-profit-success";
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
        MinerProfitRecord record = profitRecordMapper.selectById(dataId);
        return record != null && Integer.valueOf(1).equals(record.getStatus());
    }

    @Override
    public boolean ExceptionHandling(Long dataId) {
        try {
            log.info("重试框架：正在尝试重新调用合约发放收益，ID: {}", dataId);
            // 模拟调用合约接口
            String mockTxId = "0x" + System.currentTimeMillis();
            MinerProfitRecord record = profitRecordMapper.selectById(dataId);
            if (record != null) {
                record.setStatus(1);
                record.setTxId(mockTxId);
                profitRecordMapper.updateById(record);
                return true;
            }
        } catch (Exception e) {
            log.error("重试框架：收益发放重试异常，ID: {}, 原因: {}", dataId, e.getMessage());
        }
        return false;
    }
}
