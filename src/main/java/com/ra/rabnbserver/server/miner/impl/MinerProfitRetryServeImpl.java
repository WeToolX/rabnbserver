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
        statusField = "payout_status", // 监控这个业务状态
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
        return "/api/admin/miner/manual-profit-success";
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
            MinerProfitRecord record = profitRecordMapper.selectById(dataId);
            if (record == null) return false;

            // TODO: 调用收益发放合约
            boolean contractSuccess = true;

            if (contractSuccess) {
                record.setPayoutStatus(1);
                record.setTxId("0x" + System.currentTimeMillis());
                profitRecordMapper.updateById(record);
                return true;
            }
        } catch (Exception e) {
            log.error("收益发放重试异常: {}", e.getMessage());
        }
        return false;
    }
}
