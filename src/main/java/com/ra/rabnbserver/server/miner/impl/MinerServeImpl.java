package com.ra.rabnbserver.server.miner.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.MinerProfitRecordMapper;
import com.ra.rabnbserver.mapper.SystemConfigMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.pojo.MinerProfitRecord;
import com.ra.rabnbserver.pojo.SystemConfig;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserMiner;
import com.ra.rabnbserver.server.miner.MinerServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinerServeImpl extends ServiceImpl<UserMinerMapper, UserMiner> implements MinerServe {

    private final UserMapper userMapper;
    private final UserBillServe userBillServe;
    private final CardNftContract cardNftContract;
    private final SystemConfigMapper configMapper;
    private final MinerProfitRecordMapper profitRecordMapper;

    // 本地缓存：用于记录正在重试的用户ID及开始时间
    // Key: userId, Value: 重试开始的时间戳
    private static final Map<Long, Long> RETRY_CACHE = new ConcurrentHashMap<>();

    /**
     * 1. 购买/兑换矿机
     */
    @Override
    public void buyMiner(Long userId) {
        // 1. 检查是否存在 10 分钟内的重试任务
        Long startTime = RETRY_CACHE.get(userId);
        if (startTime != null) {
            if (System.currentTimeMillis() - startTime < 10 * 60 * 1000) {
                throw new BusinessException("去等待10分钟，10分钟后才可以继续购买");
            } else {
                // 超过 10 分钟了，清理掉，允许再次尝试或报错
                RETRY_CACHE.remove(userId);
            }
        }
        User user = userMapper.selectById(userId);
        try {
            // 2. 尝试调用合约销毁
//            TransactionReceipt receipt = cardNftContract.burn(user.getUserWalletAddress(), BigInteger.ONE);
//
//            if (receipt != null && "0x1".equals(receipt.getStatus())) {
//                // 销毁成功，创建矿机记录
//                createUserMinerRecord(user);
//            } else {
//                // 销毁失败 -> 进入后台异步重试逻辑
//                startAsyncRetry(user);
//                throw new BusinessException("链上销毁卡牌存在异常，请等待10分钟");
//            }
        } catch (Exception e) {
            log.error("卡牌销毁合约调用异常: {}", e.getMessage());
            startAsyncRetry(user);
            throw new BusinessException("链上销毁卡牌存在异常，请等待10分钟");
        }
    }

    /**
     * 异步重试销毁逻辑
     */
    private void startAsyncRetry(User user) {
        Long userId = user.getId();
        RETRY_CACHE.put(userId, System.currentTimeMillis());
        // 启动一个后台线程进行周期性重试 (实际项目建议用单独的任务调度类)
        CompletableFuture.runAsync(() -> {
            long begin = RETRY_CACHE.get(userId);
            boolean success = false;
            // 10 分钟内循环重试
            while (System.currentTimeMillis() - begin < 10 * 60 * 1000) {
                try {
//                    Thread.sleep(60000); // 每分钟重试一次
//                    log.info("用户 {} 销毁重试中...", userId);
//
//                    TransactionReceipt receipt = cardNftContract.burn(user.getUserWalletAddress(), BigInteger.ONE);
//                    if (receipt != null && "0x1".equals(receipt.getStatus())) {
//                        createUserMinerRecord(user);
//                        success = true;
//                        break;
//                    }
                } catch (Exception ignored) {}
            }
            if (success) {
                log.info("用户 {} 销毁卡牌重试成功，矿机已发放", userId);
            } else {
                log.error("用户 {} 销毁重试超过10分钟，已停止并通知管理员", userId);
            }
            // 无论成功还是超过10分钟，都从重试池移除
            RETRY_CACHE.remove(userId);
        });
    }

    private void createUserMinerRecord(User user) {
        UserMiner miner = new UserMiner();
        miner.setUserId(user.getId());
        miner.setWalletAddress(user.getUserWalletAddress());
        String HARDCODED_MINER_ID = "M001";
        miner.setMinerId(HARDCODED_MINER_ID);
        miner.setIsElectricityPaid(0);
        miner.setStatus(0);
        this.save(miner);
    }
    /**
     * 矿机激活与递归分销
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void activateMiner(Long userMinerId) {
        UserMiner miner = this.getById(userMinerId);
        if (miner == null) {
            throw new BusinessException("矿机不存在");
        }
        // 解析配置
        MinerSettings settings = getSettings();
        BigDecimal electricFee = settings.getElectricFee();
        // 原子扣除本地余额
        int rows = userMapper.updateBalanceAtomic(miner.getUserId(), electricFee.negate());
        if (rows == 0) {
            throw new BusinessException("账户余额不足以支付电费");
        }
        // 记账
        userBillServe.createBillAndUpdateBalance(
                miner.getUserId(), electricFee, BillType.PLATFORM, FundType.EXPENSE,
                TransactionType.EXCHANGE, "矿机激活支付电费", null, null, null);
        // 修改矿机状态
        miner.setIsElectricityPaid(1);
        miner.setStatus(1);
        miner.setPaymentDate(LocalDateTime.now());
        this.updateById(miner);
        // 递归分销逻辑
        User currentUser = userMapper.selectById(miner.getUserId());
        handleDistribution(currentUser, electricFee, settings.getDistributionRatios());
    }

    private void handleDistribution(User user, BigDecimal amount, Map<Integer, BigDecimal> ratios) {
        if (StrUtil.isBlank(user.getPath()) || "0,".equals(user.getPath())) return;
        // 获取祖先列表并反转（第一位是直接上级）
        List<Long> parents = Arrays.stream(user.getPath().split(","))
                .filter(s -> !s.equals("0") && StrUtil.isNotBlank(s))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        Collections.reverse(parents);
        for (int i = 0; i < parents.size(); i++) {
            int level = i + 1;
            if (ratios.containsKey(level)) {
                BigDecimal ratio = ratios.get(level);
                BigDecimal reward = amount.multiply(ratio);
                userBillServe.createBillAndUpdateBalance(
                        parents.get(i), reward, BillType.PLATFORM, FundType.INCOME,
                        TransactionType.REWARD, "下级激活奖励(层级:" + level + ")",
                        null, null, null);
            }
        }
    }
    private MinerSettings getSettings() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "MINER_SYSTEM_SETTINGS"));
        if (config == null) return new MinerSettings();
        return com.alibaba.fastjson2.JSON.parseObject(config.getConfigValue(), MinerSettings.class);
    }

    /**
     * 矿机收益 (由定时任务触发)
     */
    @Override
    public void processDailyProfit() {
        // 计算有效期的开始时间（当前时间往前推一个月）
        LocalDateTime expiryLimit = LocalDateTime.now().minusDays(30);
        // 构造查询条件
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMiner::getStatus, 1) // 必须是已激活状态
                .ge(UserMiner::getPaymentDate, expiryLimit); // 交费日期必须在有效期内（大于等于一个月前的时间）
        List<UserMiner> activeMiners = this.list(wrapper);
        if (activeMiners.isEmpty()) {
            log.info("当前没有处于有效期内的激活矿机，跳过收益发放。");
            return;
        }
        log.info("开始发放收益，有效矿机总数: {}", activeMiners.size());
        // 遍历发放收益
        for (UserMiner miner : activeMiners) {
            this.executeSingleMinerProfit(miner);
        }
    }

    /**
     * 单个矿机收益发放逻辑（含重试机制）
     */
    private void executeSingleMinerProfit(UserMiner miner) {
        int retries = 0;
        boolean success = false;
        while (retries < 3 && !success) {
            try {
                // 调用合约方法（需要根据您的合约实际参数调整）
                // 示例：contract.getMinerEarnings(miner.getWalletAddress(), miner.getMinerId())
                // 模拟合约返回数据
                BigDecimal amount = new BigDecimal("1.500000000000000000");
                int lockMonths = 3;
                // 记录收益记录表
                MinerProfitRecord record = new MinerProfitRecord();
                record.setUserId(miner.getUserId());
                record.setAmount(amount);
                record.setLockMonths(lockMonths);
                record.setTxId("合约链上id----模拟");
                record.setWalletAddress(miner.getWalletAddress());
                profitRecordMapper.insert(record);
                success = true;
                log.info("用户 {} 矿机收益发放成功: {} 币", miner.getUserId(), amount);
            } catch (Exception e) {
                retries++;
                log.warn("用户 {} 收益发放失败，正在进行第 {} 次重试. 错误: {}",
                        miner.getUserId(), retries, e.getMessage());
                if (retries >= 3) {
                    log.error("用户 {} 收益发放最终失败，已记录异常日志并跳过。", miner.getUserId());
                }
            }
        }
    }

}