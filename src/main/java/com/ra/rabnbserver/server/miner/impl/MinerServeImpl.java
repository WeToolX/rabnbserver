package com.ra.rabnbserver.server.miner.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.contract.CardNftContract;
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
import java.math.BigInteger;
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

    // 注入异常重试框架服务
    private final MinerPurchaseRetryServeImpl purchaseRetryServe;
    private final MinerProfitRetryServeImpl profitRetryServe;
    private final CardNftContract cardNftContract;

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

        User user = userMapper.selectById(userId);
        String walletAddress = user.getUserWalletAddress();

        // 1. 异常框架准入检查：若该地址存在大量未处理异常，拦截
        purchaseRetryServe.checkUserErr(walletAddress);

        // 2. 合约前置检查：检查用户是否授权卡牌合约，以及余额是否足够
        try {
            Boolean isApproved = cardNftContract.isApprovedForAll(walletAddress, cardNftContract.getAddress());
            if (isApproved == null || !isApproved) {
                throw new BusinessException("请先在页面完成卡牌操作授权");
            }
            BigInteger balance = cardNftContract.balanceOf(walletAddress);
            if (balance == null || balance.compareTo(BigInteger.valueOf(quantity)) < 0) {
                throw new BusinessException("卡牌余额不足，当前拥有: " + (balance == null ? 0 : balance));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("购买矿机合约状态检查失败: {}", e.getMessage());
            throw new BusinessException("区块链网络通讯异常，请稍后再试");
        }

        String minerId = switch (minerType) {
            case "0" -> "001"; // 小型
            case "1" -> "002"; // 中型
            case "2" -> "003"; // 大型
            case "3" -> "004"; // 特殊
            default -> "001";
        };

        for (int i = 0; i < quantity; i++) {
            // 3. 预创建记录：业务状态 status=0(待激活)，nftBurnStatus=0(未销毁)
            UserMiner miner = new UserMiner();
            miner.setUserId(userId);
            miner.setWalletAddress(walletAddress);
            miner.setMinerId(minerId);
            miner.setMinerType(minerType);
            miner.setNftBurnStatus(0);
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
        // 【核心物理隔离校验】只有卡牌销毁成功(nftBurnStatus=1)的矿机，才允许下一步操作
        wrapper.eq(UserMiner::getNftBurnStatus, 1);
        switch (dto.getMode()) {
            case 1: // 初始激活缴费
                wrapper.eq(UserMiner::getStatus, 0).eq(UserMiner::getMinerType, dto.getMinerType());
                break;
            case 2: // 续费
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
        if (targets.isEmpty()) {
            throw new BusinessException("没有符合条件的矿机（若刚购买，请等待卡牌销毁完成）");
        }
        BigDecimal totalFee = unitFee.multiply(new BigDecimal(targets.size()));
        if (userMapper.updateBalanceAtomic(userId, totalFee.negate()) == 0) {
            throw new BusinessException("余额不足，需支付: " + totalFee);
        }
        userBillServe.createBillAndUpdateBalance(userId, totalFee, BillType.PLATFORM, FundType.EXPENSE,
                TransactionType.EXCHANGE, "缴纳电费-模式" + dto.getMode(), null, null, null, 0,null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyAccelerationPack(Long userId, MinerAccelerationDTO dto) {
        MinerSettings settings = getSettings();
        BigDecimal unitFee = settings.getAccelerationFee();
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        // 只有运行中(status=1)、卡牌已销毁、且处于等待期内的矿机才能加速
        wrapper.eq(UserMiner::getUserId, userId)
                .eq(UserMiner::getStatus, 1)
                .eq(UserMiner::getNftBurnStatus, 1)
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
                TransactionType.PURCHASE, "购买加速包-模式" + dto.getMode(), null, null, null, 0,null);
    }

    @Override
    public void processDailyProfit() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryPoint = now.minusDays(30);
        // 筛选：NFT已销毁成功、矿机运行中、且已过等待期的矿机
        List<UserMiner> validMiners = this.list(new LambdaQueryWrapper<UserMiner>()
                .eq(UserMiner::getNftBurnStatus, 1)
                .eq(UserMiner::getStatus, 1)
                .ge(UserMiner::getPaymentDate, expiryPoint)
                .le(UserMiner::getEligibleDate, now));
        for (UserMiner miner : validMiners) {
            // 1. 创建收益记录，初始业务发放状态 payoutStatus=0
            MinerProfitRecord record = new MinerProfitRecord();
            record.setUserId(miner.getUserId());
            record.setWalletAddress(miner.getWalletAddress());
            record.setMinerType(miner.getMinerType());
            record.setAmount(new BigDecimal("1.5")); // 建议从 settings 获取
            record.setLockMonths(3);
            record.setPayoutStatus(0);
            record.setStatus(1); // 记录有效
            profitRecordMapper.insert(record);
            // 2. 提交至重试框架异步执行合约收益发放
            profitRetryServe.markAbnormal(record.getId(), record.getWalletAddress());
        }
    }


    /**
     * 获取系统设置
     * @return
     */
    private MinerSettings getSettings() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "MINER_SYSTEM_SETTINGS"));
        if (config == null) return new MinerSettings();
        return com.alibaba.fastjson2.JSON.parseObject(config.getConfigValue(), MinerSettings.class);
    }

    /**
     * 每日电费分成结算
     * 规则：
     * 1. 统计直属下级持有的活跃矿机总数决定比例
     * 2. 奖金基数：若只有1个下级缴费则按该笔计，若多于1个则扣除最高的一笔
     * 3. 只有直属下级今日产生电费才发放
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void processDailyElectricityReward() {
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime todayEnd = LocalDateTime.now().withHour(23).withMinute(59).withSecond(59);

        MinerSettings settings = getSettings();
        List<MinerSettings.RewardTier> tiers = settings.getTiers();
        if (tiers == null || tiers.isEmpty()) {
            log.warn("未配置电费分成阶梯比例，跳过结算");
            return;
        }
        // 按台数门槛从大到小排序，确保优先匹配高档位
        tiers.sort((a, b) -> b.getMinCount().compareTo(a.getMinCount()));

        // 获取系统中所有活跃矿机
        List<UserMiner> allActiveMiners = this.list(new LambdaQueryWrapper<UserMiner>().eq(UserMiner::getStatus, 1));
        if (allActiveMiners.isEmpty()) return;

        // 获取所有相关用户建立映射
        Set<Long> userIds = allActiveMiners.stream().map(UserMiner::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        // Map<上级ID, List<直属下级今日缴纳的电费金额>>
        Map<Long, List<BigDecimal>> parentSubFeesMap = new HashMap<>();
        // Map<上级ID, 直属下级活跃矿机总台数>
        Map<Long, Integer> parentSubActiveCountMap = new HashMap<>();

        for (UserMiner m : allActiveMiners) {
            User user = userMap.get(m.getUserId());
            if (user == null) continue;

            Long parentId = getDirectParentId(user);
            if (parentId == null) continue;

            // 1. 统计直属下级的活跃机器总数（用于定比例阶梯）
            parentSubActiveCountMap.merge(parentId, 1, Integer::sum);

            // 2. 统计今日缴费情况（用于算奖金基数）
            boolean isPaidToday = m.getPaymentDate() != null
                    && m.getPaymentDate().isAfter(todayStart)
                    && m.getPaymentDate().isBefore(todayEnd);

            if (isPaidToday) {
                parentSubFeesMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(settings.getElectricFee());
            }
        }

        // 开始对有缴费产生的上级进行奖金核算
        parentSubFeesMap.forEach((parentId, fees) -> {
            if (fees.isEmpty()) return;

            // A. 确定比例：基于该上级的【所有直属下级活跃机器总数】
            int totalSubMiners = parentSubActiveCountMap.getOrDefault(parentId, 0);
            BigDecimal ratio = BigDecimal.ZERO;
            for (MinerSettings.RewardTier tier : tiers) {
                if (totalSubMiners >= tier.getMinCount()) {
                    ratio = tier.getRatio();
                    break;
                }
            }

            // 未达门槛不发放
            if (ratio.compareTo(BigDecimal.ZERO) <= 0) return;

            // B. 计算基数：1人缴费不剔除，多人缴费剔除最高一项
            BigDecimal bonusBase;
            if (fees.size() == 1) {
                bonusBase = fees.get(0);
            } else {
                BigDecimal maxFee = Collections.max(fees);
                BigDecimal totalFee = fees.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                bonusBase = totalFee.subtract(maxFee);
            }

            // C. 执行发放
            if (bonusBase.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rewardAmount = bonusBase.multiply(ratio);
                try {
                    userBillServe.createBillAndUpdateBalance(
                            parentId, rewardAmount, BillType.PLATFORM, FundType.INCOME,
                            TransactionType.REWARD,
                            String.format("直属下级电费分成(下级总机:%d, 比例:%.2f%%, 缴费人数:%d)",
                                    totalSubMiners, ratio.multiply(new BigDecimal("100")), fees.size()),
                            null, null, null, 0,null
                    );
                    log.info("上级 {} 奖励发放成功: {}, 比例: {}", parentId, rewardAmount, ratio);
                } catch (Exception e) {
                    log.error("结算上级 {} 分成失败: {}", parentId, e.getMessage());
                }
            }
        });
    }

    /**
     * 解析直属上级ID
     */
    private Long getDirectParentId(User user) {
        if (user == null || StrUtil.isBlank(user.getPath()) || "0,".equals(user.getPath())) {
            return null;
        }
        String[] parts = user.getPath().split(",");
        if (parts.length < 2) return null;
        try {
            // path 格式 "0,1,5,10," -> 取最后一位有效数字
            return Long.parseLong(parts[parts.length - 1]);
        } catch (Exception e) {
            return null;
        }
    }
}