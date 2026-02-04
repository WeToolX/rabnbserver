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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinerServeImpl extends ServiceImpl<UserMinerMapper, UserMiner> implements MinerServe {

    private final UserMapper userMapper;
    private final UserBillServe userBillServe;
    private final MinerProfitRecordMapper profitRecordMapper;
    private final SystemConfigMapper configMapper;
    // private final CardNftContract cardNftContract; // 合约先注释

    // 本地缓存：用于拦截10分钟内的重复购买/重试任务
    private static final Map<Long, Long> RETRY_CACHE = new ConcurrentHashMap<>();

    @Override
    public IPage<UserMiner> getUserMinerPage(Long userId, MinerQueryDTO query) {
        // 初始化分页对象
        Page<UserMiner> pageParam = new Page<>(query.getPage(), query.getSize());
        // 构造查询条件
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        // 强制限定为当前用户
        wrapper.eq(UserMiner::getUserId, userId);
        // 动态组合筛选条件 (判断非空或非null)
        wrapper.eq(StrUtil.isNotBlank(query.getMinerType()), UserMiner::getMinerType, query.getMinerType());
        wrapper.eq(query.getStatus() != null, UserMiner::getStatus, query.getStatus());
        wrapper.eq(query.getIsElectricityPaid() != null, UserMiner::getIsElectricityPaid, query.getIsElectricityPaid());
        wrapper.eq(query.getIsAccelerated() != null, UserMiner::getIsAccelerated, query.getIsAccelerated());
        wrapper.like(StrUtil.isNotBlank(query.getMinerId()), UserMiner::getMinerId, query.getMinerId());
        // 时间范围筛选
        if (StrUtil.isNotBlank(query.getStartTime())) {
            wrapper.ge(UserMiner::getCreateTime, cn.hutool.core.date.DateUtil.parse(query.getStartTime()).toLocalDateTime());
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.le(UserMiner::getCreateTime, cn.hutool.core.date.DateUtil.parse(query.getEndTime()).toLocalDateTime());
        }
        // 排序：按创建时间倒序
        wrapper.orderByDesc(UserMiner::getCreateTime);
        return this.page(pageParam, wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyMinerBatch(Long userId, String minerType, int quantity) {
        if (quantity <= 0) throw new BusinessException("数量不合法");
        // 10分钟防抖
        Long lastTime = RETRY_CACHE.get(userId);
        if (lastTime != null && (System.currentTimeMillis() - lastTime < 10 * 60 * 1000)) {
            throw new BusinessException("操作过于频繁，请10分钟后再试");
        }
        User user = userMapper.selectById(userId);
        try {
            // 模拟合约批量销毁卡牌
            boolean contractSuccess = true;
            if (contractSuccess) {
                for (int i = 0; i < quantity; i++) {
                    doCreateMiner(user, minerType);
                }
            } else {
                throw new BusinessException("合约销毁失败");
            }
        } catch (Exception e) {
            log.error("批量购买失败: {}", e.getMessage());
            throw new BusinessException("兑换失败: " + e.getMessage());
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
            case 1: // 待激活
                wrapper.eq(UserMiner::getStatus, 0).eq(UserMiner::getMinerType, dto.getMinerType());
                break;
            case 2: // 即将到期
                LocalDateTime targetDate = expiryLimit.plusDays(dto.getDays());
                wrapper.eq(UserMiner::getStatus, 1).eq(UserMiner::getMinerType, dto.getMinerType())
                        .ge(UserMiner::getPaymentDate, expiryLimit).le(UserMiner::getPaymentDate, targetDate);
                if (dto.getQuantity() != null) wrapper.last("LIMIT " + dto.getQuantity());
                break;
            case 3: // 已到期 (按类型)
                wrapper.eq(UserMiner::getMinerType, dto.getMinerType())
                        .and(w -> w.lt(UserMiner::getPaymentDate, expiryLimit).or().eq(UserMiner::getStatus, 0));
                break;
            case 4: // 全部已到期/未激活
                wrapper.and(w -> w.lt(UserMiner::getPaymentDate, expiryLimit).or().eq(UserMiner::getStatus, 0));
                break;
            default: throw new BusinessException("模式错误");
        }
        List<UserMiner> targets = this.list(wrapper);
        if (targets.isEmpty()) throw new BusinessException("没有符合条件的矿机");
        BigDecimal totalFee = unitFee.multiply(new BigDecimal(targets.size()));
        if (userMapper.updateBalanceAtomic(userId, totalFee.negate()) == 0) {
            throw new BusinessException("余额不足，需支付: " + totalFee);
        }
        User user = userMapper.selectById(userId);
        for (UserMiner m : targets) {
            m.setStatus(1);
            m.setIsElectricityPaid(1);
            m.setPaymentDate(now);
            this.updateById(m);
            // 分销
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
        wrapper.eq(UserMiner::getUserId, userId).eq(UserMiner::getIsAccelerated, 0).gt(UserMiner::getEligibleDate, now);
        switch (dto.getMode()) {
            case 1: wrapper.eq(UserMiner::getMinerType, dto.getMinerType()); break;
            case 2: wrapper.eq(UserMiner::getMinerType, dto.getMinerType()).last("LIMIT " + dto.getQuantity()); break;
            case 3: break;
            case 4: wrapper.eq(UserMiner::getId, dto.getUserMinerId()); break;
            default: throw new BusinessException("模式错误");
        }
        List<UserMiner> targets = this.list(wrapper);
        if (targets.isEmpty()) throw new BusinessException("无可加速的矿机");
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

    private void doCreateMiner(User user, String minerType) {
        UserMiner miner = new UserMiner();
        miner.setUserId(user.getId());
        miner.setWalletAddress(user.getUserWalletAddress());
        miner.setMinerId("M001");
        miner.setMinerType(minerType);
        miner.setStatus(0);
        miner.setEligibleDate(LocalDateTime.now().plusDays(15));
        this.save(miner);
    }


    /**
     * 每日收益发放 (定时任务)
     */
    @Override
    public void processDailyProfit() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryPoint = now.minusDays(30);
        // 提取逻辑：1.已激活 2.电费30天内 3.已过起算等待期
        List<UserMiner> validMiners = this.list(new LambdaQueryWrapper<UserMiner>()
                .eq(UserMiner::getStatus, 1)
                .ge(UserMiner::getPaymentDate, expiryPoint)
                .le(UserMiner::getEligibleDate, now));
        for (UserMiner miner : validMiners) {
            int retries = 0;
            while (retries < 3) {
                try {
                    // --- 调用合约获取收益 (模拟) ---
                    // Map result = cardNftContract.getProfit(miner.getWalletAddress());
                    BigDecimal amount = new BigDecimal("1.5");
                    Integer locks = 3;

                    MinerProfitRecord record = new MinerProfitRecord();
                    record.setUserId(miner.getUserId());
                    record.setMinerType(miner.getMinerType());
                    record.setAmount(amount);
                    record.setLockMonths(locks);
                    profitRecordMapper.insert(record);
                    break;
                } catch (Exception e) {
                    retries++;
                    if (retries == 3) log.error("矿机收益发放失败，跳过: {}", miner.getId());
                }
            }
        }
    }


    private void executeDistribution(User user, BigDecimal amount, Map<Integer, BigDecimal> ratios) {
        if (StrUtil.isBlank(user.getPath()) || "0,".equals(user.getPath())) return;
        List<Long> parents = Arrays.asList(user.getPath().split(","))
                .stream().filter(s -> !s.equals("0") && StrUtil.isNotBlank(s))
                .map(Long::parseLong).collect(java.util.stream.Collectors.toList());
        java.util.Collections.reverse(parents);

        for (int i = 0; i < parents.size(); i++) {
            int level = i + 1;
            if (ratios.containsKey(level)) {
                userBillServe.createBillAndUpdateBalance(parents.get(i), amount.multiply(ratios.get(level)),
                        BillType.PLATFORM, FundType.INCOME, TransactionType.REWARD,
                        "下级激活奖励(层级:" + level + ")", null, null, null);
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