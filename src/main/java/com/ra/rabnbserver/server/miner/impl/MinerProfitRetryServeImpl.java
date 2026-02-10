package com.ra.rabnbserver.server.miner.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ra.rabnbserver.contract.AionContract;
import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.exception.AionContractException;
import com.ra.rabnbserver.mapper.MinerProfitRecordMapper;
import com.ra.rabnbserver.pojo.MinerProfitRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

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
        minIntervalSeconds = 60,
        timeoutSeconds = 600,
        maxRetryCount = 10,
        manualRemindIntervalSeconds = 10
)
public class MinerProfitRetryServeImpl extends AbstractAbnormalRetryService {

    private final MinerProfitRecordMapper profitRecordMapper;
    private final AionContract aionContract;

    public MinerProfitRetryServeImpl(AbnormalRetryManager abnormalRetryManager, MinerProfitRecordMapper profitRecordMapper, AionContract aionContract) {
        super(abnormalRetryManager);
        this.profitRecordMapper = profitRecordMapper;
        this.aionContract = aionContract;
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

    /**
     * 十六进制状态解析工具
     */
    private String parseChainStatus(String status) {
        if (StrUtil.isBlank(status)) return "0";
        if (status.startsWith("0x")) {
            try {
                return new BigInteger(status.substring(2), 16).toString();
            } catch (Exception e) {
                return status;
            }
        }
        return status;
    }

    @Override
    public boolean checkStatus(Long dataId) {
        MinerProfitRecord record = profitRecordMapper.selectById(dataId);
        if (record == null) return false;
        if (Integer.valueOf(1).equals(record.getPayoutStatus())) return true;

        try {
            if (record.getActualOrderId() == null) return false;

            // 调用 getOrder。如果订单不存在，合约会 revert，AionContract 会抛出 AionContractException
            AionContract.OrderRecord order = aionContract.getOrder(record.getWalletAddress(), BigInteger.valueOf(record.getActualOrderId()));

            // 若未抛异常且 order 正常返回，说明订单已在链上
            if (order != null && order.getStatus().intValue() == 0) {
                profitRecordMapper.update(null, new LambdaUpdateWrapper<MinerProfitRecord>()
                        .eq(MinerProfitRecord::getId, dataId)
                        .set(MinerProfitRecord::getPayoutStatus, 1));
                return true;
            }
        } catch (AionContractException e) {
            // 错误码 8 代表 ORDER_NOT_FOUND，说明订单确实没发过
            if (Integer.valueOf(8).equals(e.getErrorCode())) {
                log.info("检查记录 {}: 链上订单不存在，准备重试", dataId);
                return false;
            }
            log.error("收益状态检查合约业务异常 ID {}: {}", dataId, e.getDecodedDetail());
        } catch (Exception e) {
            log.error("收益状态检查系统异常 ID {}: {}", dataId, e.getMessage());
        }
        return false;
    }


    @Override
    public boolean ExceptionHandling(Long dataId) {
        MinerProfitRecord record = profitRecordMapper.selectById(dataId);
        if (record == null) return false;

        try {
            Long orderIdToUse = record.getActualOrderId();
            boolean needNewId = false;

            if (orderIdToUse != null) {
                try {
                    AionContract.OrderRecord oldOrder = aionContract.getOrder(record.getWalletAddress(), BigInteger.valueOf(orderIdToUse));
                    // 订单存在但状态非0（失败），需要换新单号重发
                    if (oldOrder.getStatus().intValue() != 0) {
                        needNewId = true;
                    }
                } catch (AionContractException e) {
                    // 若订单不存在（Code 8），直接沿用旧单号即可
                    if (!Integer.valueOf(8).equals(e.getErrorCode())) {
                        needNewId = true; // 其他异常也换号尝试
                    }
                }
            } else {
                needNewId = true;
            }

            if (needNewId) {
                orderIdToUse = Long.parseLong(dataId + "" + (System.currentTimeMillis() % 1000000));
                profitRecordMapper.update(null, new LambdaUpdateWrapper<MinerProfitRecord>()
                        .eq(MinerProfitRecord::getId, dataId)
                        .set(MinerProfitRecord::getActualOrderId, orderIdToUse));
            }

            TransactionReceipt receipt = aionContract.allocateEmissionToLocks(
                    record.getWalletAddress(),
                    record.getAmount().toBigInteger(),
                    record.getLockType(),
                    record.getDistType(),
                    BigInteger.valueOf(orderIdToUse)
            );

            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                profitRecordMapper.update(null, new LambdaUpdateWrapper<MinerProfitRecord>()
                        .eq(MinerProfitRecord::getId, dataId)
                        .set(MinerProfitRecord::getPayoutStatus, 1)
                        .set(MinerProfitRecord::getTxId, receipt.getTransactionHash()));
                return true;
            }
        } catch (AionContractException e) {
            log.error("收益补发业务异常 ID {}: {}", dataId, e.getDecodedDetail());
        } catch (Exception e) {
            log.error("收益补发系统异常 ID {}: {}", dataId, e.getMessage());
        }
        return false;
    }
}
