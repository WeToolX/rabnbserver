package com.ra.rabnbserver.server.miner.impl;

import cn.hutool.core.util.StrUtil;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.CardNftContractV1;
import com.ra.rabnbserver.exception.Abnormal.annotation.AbnormalRetryConfig;
import com.ra.rabnbserver.exception.Abnormal.core.AbstractAbnormalRetryService;
import com.ra.rabnbserver.exception.Abnormal.core.AbnormalRetryManager;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.miner.MinerServe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;

@Slf4j
@Service
@AbnormalRetryConfig(
        table = "user_miner",
        serviceName = "矿机购买卡牌销毁",
        idField = "id",
        userField = "wallet_address",
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
    private final UserMapper userMapper;
    private final CardNftContract cardNftContract;
    private final CardNftContractV1  cardNftContractV1;
    private final ObjectProvider<MinerServe> minerServeProvider;

    public MinerPurchaseRetryServeImpl(AbnormalRetryManager abnormalRetryManager, UserMinerMapper userMinerMapper, UserMapper userMapper, CardNftContract cardNftContract, CardNftContractV1 cardNftContractV1, ObjectProvider<MinerServe> minerServeProvider) {
        super(abnormalRetryManager);
        this.userMinerMapper = userMinerMapper;
        this.userMapper = userMapper;
        this.cardNftContract = cardNftContract;
        this.cardNftContractV1 = cardNftContractV1;
        this.minerServeProvider = minerServeProvider;
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
        recalculateDirectParentGrade(dataId);
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
            if (miner.getNftCardId() == null || miner.getNftBurnOrderId() == null) {
                log.error("销毁重试缺少必要参数，ID={}, cardId={}, orderId={}", dataId, miner.getNftCardId(), miner.getNftBurnOrderId());
                return false;
            }
            Boolean approved = cardNftContract.isApprovedForCurrentOperator(miner.getWalletAddress());
            if (!Boolean.TRUE.equals(approved)) {
                log.warn("销毁重试检测到用户未授权，矿机ID={}, 用户地址={}, 操作地址={}",
                        dataId,
                        miner.getWalletAddress(),
                        cardNftContract.getOperatorAddress());
                return false;
            }
            //todo 重试方法销毁卡牌
            TransactionReceipt receipt = cardNftContract.burnWithOrder(
                    miner.getWalletAddress(),
                    BigInteger.valueOf(miner.getNftCardId()),
                    BigInteger.ONE,
                    miner.getNftBurnOrderId()
            );
//            TransactionReceipt receipt = cardNftContractV1.burnUser(
//                    miner.getWalletAddress(),
//                    BigInteger.ONE
//            );
            // ContractBase 及其子类在 status=0x0 时会抛出 ContractCallException
            // 此处逻辑只需判断 receipt 是否成功到达
            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                miner.setNftBurnStatus(1);
                userMinerMapper.updateById(miner);
                recalculateDirectParentGrade(miner);
                log.info("矿机ID: {} 链上补录销毁成功", dataId);
                return true;
            }
        } catch (Exception e) {
            log.error("销毁重试执行异常, ID: {}, 原因: {}", dataId, e.getMessage());
        }
        return false;
    }

    private void recalculateDirectParentGrade(Long minerId) {
        UserMiner miner = userMinerMapper.selectById(minerId);
        recalculateDirectParentGrade(miner);
    }

    private void recalculateDirectParentGrade(UserMiner miner) {
        if (miner == null || miner.getUserId() == null) {
            return;
        }
        User user = userMapper.selectById(miner.getUserId());
        Long parentId = getDirectParentId(user);
        if (parentId == null || parentId <= 0) {
            return;
        }
        MinerServe minerServe = minerServeProvider.getIfAvailable();
        if (minerServe != null) {
            minerServe.recalculateUserGrade(parentId);
        }
    }

    private Long getDirectParentId(User user) {
        if (user != null && user.getParentId() != null && user.getParentId() > 0) {
            return user.getParentId();
        }
        if (user == null || StrUtil.isBlank(user.getPath()) || "0,".equals(user.getPath())) {
            return null;
        }
        String[] parts = user.getPath().split(",");
        if (parts.length < 2) {
            return null;
        }
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }
}
