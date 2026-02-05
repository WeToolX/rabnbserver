package com.ra.rabnbserver.server.miner.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.dto.MinerAccelerationDTO;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.MinerProfitRecordMapper;
import com.ra.rabnbserver.mapper.SystemConfigMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.pojo.*;
import com.ra.rabnbserver.server.miner.MinerServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinerServeImpl extends ServiceImpl<UserMinerMapper, UserMiner> implements MinerServe {

    private final UserMapper userMapper;
    private final UserBillServe userBillServe;
    private final MinerProfitRecordMapper profitRecordMapper;
    private final SystemConfigMapper configMapper;

    // 注入专门的重试处理服务
    // 注意：请确保这两个类中已经重写并将 markAbnormal/ProcessingSuccessful 等方法设为 public
    private final MinerPurchaseRetryServeImpl purchaseRetryServe;
    private final MinerProfitRetryServeImpl profitRetryServe;

    @Override
    public IPage<UserMiner> getUserMinerPage(Long userId, MinerQueryDTO query) {
        Page<UserMiner> pageParam = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMiner::getUserId, userId);

        // 动态条件
        wrapper.eq(StrUtil.isNotBlank(query.getMinerType()), UserMiner::getMinerType, query.getMinerType());
        wrapper.eq(query.getStatus() != null, UserMiner::getStatus, query.getStatus());
        wrapper.eq(query.getIsElectricityPaid() != null, UserMiner::getIsElectricityPaid, query.getIsElectricityPaid());
        wrapper.eq(query.getIsAccelerated() != null, UserMiner::getIsAccelerated, query.getIsAccelerated());
        wrapper.like(StrUtil.isNotBlank(query.getMinerId()), UserMiner::getMinerId, query.getMinerId());

        if (StrUtil.isNotBlank(query.getStartTime())) {
            wrapper.ge(UserMiner::getCreateTime, cn.hutool.core.date.DateUtil.parse(query.getStartTime()).toLocalDateTime());
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.le(UserMiner::getCreateTime, cn.hutool.core.date.DateUtil.parse(query.getEndTime()).toLocalDateTime());
        }

        wrapper.orderByDesc(UserMiner::getCreateTime);
        return this.page(pageParam, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyMinerBatch(Long userId, String minerType, int quantity) {
        if (quantity <= 0) throw new BusinessException("数量不合法");

        // 1. 异常框架准入检查：如果用户当前已有大量卡死且需要人工处理的异常，禁止继续购买
        purchaseRetryServe.checkUserErr(String.valueOf(userId));

        User user = userMapper.selectById(userId);
        for (int i = 0; i < quantity; i++) {
            // 2. 预创建记录：状态 status=0(待激活)，技术状态默认为 2000(处理中)
            UserMiner miner = new UserMiner();
            miner.setUserId(user.getId());
            miner.setWalletAddress(user.getUserWalletAddress());
            miner.setMinerId("M-" + System.nanoTime());
            miner.setMinerType(minerType);
            miner.setStatus(0);
            miner.setEligibleDate(LocalDateTime.now().plusDays(15));
            this.save(miner);

            try {
                // 3. 尝试执行合约逻辑（销毁卡牌）
                boolean contractSuccess = true; // 模拟合约调用
                if (contractSuccess) {
                    // 4. 初次执行成功：同步更新异常框架状态为 2001
                    // 只有 err_status 变为 2001/2002，该矿机才允许被“缴纳电费”
                    purchaseRetryServe.ProcessingSuccessful(miner.getId());
                } else {
                    // 5. 合约明确返回失败：标记异常，进入自动重试队列
                    purchaseRetryServe.markAbnormal(miner.getId());
                }
            } catch (Exception e) {
                // 6. 异常或超时：标记异常，交由框架自动补齐字段并开始后台重试
                log.error("购买矿机合约调用异常，已存入重试队列。ID: {}, 原因: {}", miner.getId(), e.getMessage());
                purchaseRetryServe.markAbnormal(miner.getId());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void payElectricity(Long userId, MinerElectricityDTO dto) {
        MinerSettings settings = getSettings();
        BigDecimal unitFee = settings.getElectricFee();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryLimit = now.minusDays(30);

        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMiner::getUserId, userId);

        switch (dto.getMode()) {
            case 1: // 待激活矿机缴费
                wrapper.eq(UserMiner::getStatus, 0)
                        .eq(UserMiner::getMinerType, dto.getMinerType());
                // 【核心安全校验】只有合约处理成功(2001)或管理员人工确认成功(2002)的矿机，才允许缴费激活
                wrapper.and(w -> w.eq(UserMiner::getErrStatus, 2001).or().eq(UserMiner::getErrStatus, 2002));
                break;

            case 2: // 即将到期续费
                LocalDateTime targetDate = expiryLimit.plusDays(dto.getDays());
                wrapper.eq(UserMiner::getStatus, 1).eq(UserMiner::getMinerType, dto.getMinerType())
                        .ge(UserMiner::getPaymentDate, expiryLimit).le(UserMiner::getPaymentDate, targetDate);
                if (dto.getQuantity() != null) wrapper.last("LIMIT " + dto.getQuantity());
                break;

            case 3: // 已到期 (按类型)
                wrapper.eq(UserMiner::getMinerType, dto.getMinerType())
                        .and(w -> w.lt(UserMiner::getPaymentDate, expiryLimit).or().eq(UserMiner::getStatus, 0));
                // 同样需要满足技术状态成功
                wrapper.and(w -> w.eq(UserMiner::getErrStatus, 2001).or().eq(UserMiner::getErrStatus, 2002));
                break;

            case 4: // 全部已到期/未激活
                wrapper.and(w -> w.lt(UserMiner::getPaymentDate, expiryLimit).or().eq(UserMiner::getStatus, 0));
                wrapper.and(w -> w.eq(UserMiner::getErrStatus, 2001).or().eq(UserMiner::getErrStatus, 2002));
                break;
            default: throw new BusinessException("模式错误");
        }

        List<UserMiner> targets = this.list(wrapper);
        if (targets.isEmpty()) {
            // 如果查不到数据，大概率是卡牌销毁还没成功
            throw new BusinessException("没有符合条件的矿机（若刚购买，请等待系统处理完成）");
        }

        BigDecimal totalFee = unitFee.multiply(new BigDecimal(targets.size()));
        if (userMapper.updateBalanceAtomic(userId, totalFee.negate()) == 0) {
            throw new BusinessException("余额不足，需支付: " + totalFee);
        }

        User user = userMapper.selectById(userId);
        for (UserMiner m : targets) {
            m.setStatus(1); // 激活，进入正式收益状态
            m.setIsElectricityPaid(1);
            m.setPaymentDate(now);
            this.updateById(m);
            executeDistribution(user, unitFee, settings.getDistributionRatios());
        }
        userBillServe.createBillAndUpdateBalance(userId, totalFee, BillType.PLATFORM, FundType.EXPENSE,
                TransactionType.EXCHANGE, "缴纳电费-模式" + dto.getMode(), null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyAccelerationPack(Long userId, MinerAccelerationDTO dto) {
        MinerSettings settings = getSettings();
        BigDecimal unitFee = settings.getAccelerationFee();
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        // 只有已经激活运行 (status=1) 且处于等待期内的矿机才能加速
        wrapper.eq(UserMiner::getUserId, userId)
                .eq(UserMiner::getStatus, 1)
                .eq(UserMiner::getIsAccelerated, 0)
                .gt(UserMiner::getEligibleDate, now);

        switch (dto.getMode()) {
            case 1: wrapper.eq(UserMiner::getMinerType, dto.getMinerType()); break;
            case 2: wrapper.eq(UserMiner::getMinerType, dto.getMinerType()).last("LIMIT " + dto.getQuantity()); break;
            case 3: break;
            case 4: wrapper.eq(UserMiner::getId, dto.getUserMinerId()); break;
            default: throw new BusinessException("模式错误");
        }

        List<UserMiner> targets = this.list(wrapper);
        if (targets.isEmpty()) throw new BusinessException("当前无可加速的矿机");

        BigDecimal totalFee = unitFee.multiply(new BigDecimal(targets.size()));
        if (userMapper.updateBalanceAtomic(userId, totalFee.negate()) == 0) {
            throw new BusinessException("余额不足");
        }
        for (UserMiner m : targets) {
            LocalDateTime limit = m.getCreateTime().plusDays(3);
            m.setEligibleDate(now.isBefore(limit) ? limit : now);
            m.setIsAccelerated(1);
            this.updateById(m);
        }
        userBillServe.createBillAndUpdateBalance(userId, totalFee, BillType.PLATFORM, FundType.EXPENSE,
                TransactionType.PURCHASE, "购买加速包-模式" + dto.getMode(), null, null, null);
    }

    @Override
    public void processDailyProfit() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryPoint = now.minusDays(30);

        // 由于 payElectricity 已经拦截了合约未成功的矿机，
        // 这里取出 status=1 的矿机必定是：合约已确认成功 且 已付电费 且 在有效期内的。
        List<UserMiner> validMiners = this.list(new LambdaQueryWrapper<UserMiner>()
                .eq(UserMiner::getStatus, 1)
                .ge(UserMiner::getPaymentDate, expiryPoint)
                .le(UserMiner::getEligibleDate, now));

        for (UserMiner miner : validMiners) {
            // 1. 创建收益记录，初始业务状态 status=0
            MinerProfitRecord record = new MinerProfitRecord();
            record.setUserId(miner.getUserId());
            record.setWalletAddress(miner.getWalletAddress());
            record.setMinerType(miner.getMinerType());
            record.setAmount(new BigDecimal("1.5"));
            record.setLockMonths(3);
            record.setStatus(0);
            profitRecordMapper.insert(record);

            try {
                // 2. 尝试执行合约收益分发
                boolean success = true;
                if (success) {
                    record.setStatus(1);
                    record.setTxId("TX-" + System.currentTimeMillis());
                    profitRecordMapper.updateById(record);
                    // 3. 同步框架状态为成功
                    profitRetryServe.ProcessingSuccessful(record.getId());
                } else {
                    // 4. 合约显式失败：标记异常
                    profitRetryServe.markAbnormal(record.getId());
                }
            } catch (Exception e) {
                log.error("发放收益合约异常，记录ID: {}, 进入异常框架重试", record.getId());
                // 5. 异常标记：由 MinerProfitRetryServeImpl 负责后续自动重试
                profitRetryServe.markAbnormal(record.getId());
            }
        }
    }

    /**
     * 执行电费分润分销
     * @param user   当前缴费的用户
     * @param amount 缴纳的金额基数
     * @param ratios 分润比例配置 Map<层级, 比例>
     */
    private void executeDistribution(User user, BigDecimal amount, Map<Integer, BigDecimal> ratios) {
        // 基础校验：如果没有上级路径或没有配置比例，直接返回
        if (StrUtil.isBlank(user.getPath()) || "0,".equals(user.getPath()) || ratios == null || ratios.isEmpty()) {
            return;
        }

        // 解析上级路径并反转
        // 原始 path 示例: "0,1,5,10," -> 解析并反转后: [10, 5, 1] (10是直属1级上级)
        List<Long> parents = Arrays.stream(user.getPath().split(","))
                .filter(s -> StrUtil.isNotBlank(s) && !"0".equals(s))
                .map(Long::parseLong)
                .collect(Collectors.toList());
        Collections.reverse(parents);
        // 确定后台配置的最大分润层级（例如配置了 {1: 0.1, 3: 0.05}，maxConfigLevel 就是 3）
        int maxConfigLevel = ratios.keySet().stream()
                .max(Integer::compare)
                .orElse(0);
        // 开始向上追溯分润
        for (int i = 0; i < parents.size(); i++) {
            // 当前代数（1代表直属，2代表上上的上级...）
            int currentLevel = i + 1;
            // 如果当前代数已经超过了后台设置的最大代数，直接结束循环
            if (currentLevel > maxConfigLevel) {
                log.debug("用户 {} 的分润已达到设置的最大层级 {}，停止向上追溯", user.getId(), maxConfigLevel);
                break;
            }
            BigDecimal ratio = ratios.get(currentLevel);
            // 检查配置是否存在
            if (ratio != null) {
                // 如果比例 > 1 (代表 > 100%，通常是后台误填了整数如 10) 或比例为负数
                if (ratio.compareTo(BigDecimal.ONE) > 0 || ratio.compareTo(BigDecimal.ZERO) < 0) {
                    log.error("【严重配置异常】分润中断！检测到非法的比例配置：层级={}, 比例={}。" +
                            "预期应为 0 到 1 之间的小数（如 0.1 代表 10%）。" +
                            "为了防止资金安全风险，已停止发放后续所有奖励！", currentLevel, ratio);
                    // 发现配置错误，立即退出方法，不再给任何人发钱，保护系统账户
                    return;
                }
                // 比例合法且大于 0 才执行发放
                if (ratio.compareTo(BigDecimal.ZERO) > 0) {
                    Long parentId = parents.get(i);
                    BigDecimal rewardAmount = amount.multiply(ratio);
                    try {
                        // 执行账单创建和余额增加
                        userBillServe.createBillAndUpdateBalance(
                                parentId,
                                rewardAmount,
                                BillType.PLATFORM,
                                FundType.INCOME,
                                TransactionType.REWARD,
                                String.format("下级激活奖励(来自用户ID:%d, 层级:%d)", user.getId(), currentLevel),
                                null, null, null
                        );
                        log.info("奖励发放成功: 用户ID {} -> 上级ID {}(第{}层), 金额: {}",
                                user.getId(), parentId, currentLevel, rewardAmount);
                    } catch (Exception e) {
                        log.error("发放层级奖励异常，上级ID: {}, 错误信息: {}", parentId, e.getMessage());
                    }
                }
            } else {
                // 如果该层级没有配置比例（层级跳跃），则打印日志并继续寻找更高层级
                log.debug("第 {} 层未配置分润比例，自动跳过并查找下一层", currentLevel);
            }
        }
    }

    private MinerSettings getSettings() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "MINER_SYSTEM_SETTINGS"));
        if (config == null) return new MinerSettings();
        return com.alibaba.fastjson2.JSON.parseObject(config.getConfigValue(), MinerSettings.class);
    }
}