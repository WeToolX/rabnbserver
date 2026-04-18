package com.ra.rabnbserver.server.miner.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.CreateUserBillVO;
import com.ra.rabnbserver.VO.GetAdminClaimVO;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.contract.AionContract;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.CardNftContractV1;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.dto.adminMinerAction.AdminMinerActionDTO;
import com.ra.rabnbserver.dto.adminMinerAction.FragmentExchangeNftDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.AionContractException;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.SystemConfigMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.pojo.*;
import com.ra.rabnbserver.server.miner.MinerServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import com.ra.rabnbserver.server.user.UserServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MinerServeImpl extends ServiceImpl<UserMinerMapper, UserMiner> implements MinerServe {
    private final UserMapper userMapper;
    private final UserBillServe userBillServe;
    private final SystemConfigMapper configMapper;
    private final IService<MinerProfitRecord> profitRecordService;
    private final UserServe userService;
    private final AionContract aionContract;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;
    private final MinerPurchaseRetryServeImpl purchaseRetryServe;
    private final CardNftContract cardNftContract;
    private final CardNftContractV1 cardNftContractV1;

    @Override
    public IPage<UserMiner> getUserMinerPage(Long userId, MinerQueryDTO query) {
        Page<UserMiner> pageParam = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        if (userId != 0) {
            wrapper.eq(UserMiner::getUserId, userId);
        }
        // 动态条件
        wrapper.eq(StrUtil.isNotBlank(query.getMinerType()), UserMiner::getMinerType, query.getMinerType());
        wrapper.eq(query.getStatus() != null, UserMiner::getStatus, query.getStatus());
        wrapper.eq(query.getIsElectricityPaid() != null, UserMiner::getIsElectricityPaid, query.getIsElectricityPaid());
        if (query.getExpiryStatus() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiredLine = now.minusDays(30); // 30天前的时间点
            LocalDateTime warningLine = now.minusDays(25); // 25天前的时间点（即距离过期还有5天）
            switch (query.getExpiryStatus()) {
                case 1: // 已到期：paymentDate < 30天前，或者从未缴费 status=0/paymentDate is null)
                    wrapper.lt(UserMiner::getPaymentDate, expiredLine)
                            .eq(UserMiner::getStatus, 1);
                    break;
                case 2: // 未到期（正常）：paymentDate > 25天前
                    wrapper.gt(UserMiner::getPaymentDate, warningLine)
                            .eq(UserMiner::getStatus, 1);
                    break;
                case 3: // 即将到期：25天前 >= paymentDate >= 30天前
                    wrapper.ge(UserMiner::getPaymentDate, expiredLine)
                            .le(UserMiner::getPaymentDate, warningLine)
                            .eq(UserMiner::getStatus, 1);
                    break;
            }
        }
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
    public void buyMinerBatch(Long userId, String minerType, int quantity, Integer cardId) {
        if (quantity <= 0) {
            throw new BusinessException("数量不合法");
        }
        if (cardId == null) {
            throw new BusinessException("卡牌ID不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(0, "用户不存在");
        }
        String walletAddress = user.getUserWalletAddress();
        purchaseRetryServe.checkUserErr(walletAddress);
        try {
            BigInteger balance = cardNftContractV1.balanceOf(walletAddress);
            if (balance == null || balance.compareTo(BigInteger.valueOf(quantity)) < 0) {
                throw new BusinessException("卡牌余额不足，当前拥有: " + (balance == null ? 0 : balance));
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("卡牌状态检查异常: {}", e.getMessage());
            throw new BusinessException("区块链网络通讯失败，请稍后");
        }

        String safeMinerType = switch (minerType) {
            case "0", "1", "2", "3" -> minerType;
            default -> "0";
        };

        List<UserMiner> createdMiners = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            UserMiner miner = new UserMiner();
            miner.setUserId(userId);
            miner.setWalletAddress(walletAddress);
            miner.setMinerId(safeMinerType);
            miner.setMinerType(safeMinerType);
            miner.setNftBurnStatus(0);
            miner.setNftCardId(cardId);
            miner.setNftBurnOrderId("NFT_BURN_" + userId + "_" + IdWorker.getIdStr());
            miner.setStatus(0);
            miner.setIsElectricityPaid(0);
            miner.setPaymentDate(null);
            miner.setEligibleDate(null);
            miner.setIsAccelerated(0);
            this.save(miner);
            createdMiners.add(miner);
        }

        for (UserMiner miner : createdMiners) {
            try {
                TransactionReceipt receipt = cardNftContract.burnWithOrder(
                        walletAddress,
                        BigInteger.valueOf(cardId),
                        BigInteger.ONE,
                        miner.getNftBurnOrderId()
                );
                if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                    this.lambdaUpdate()
                            .set(UserMiner::getNftBurnStatus, 1)
                            .eq(UserMiner::getId, miner.getId())
                            .update();
                } else {
                    purchaseRetryServe.markAbnormal(miner.getId());
                }
            } catch (Exception e) {
                log.error("销毁调用异常，矿机ID: {}, 原因: {}", miner.getId(), e.getMessage());
                purchaseRetryServe.markAbnormal(miner.getId());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void payElectricity(Long userId, MinerElectricityDTO dto) {
        log.info("缴纳电费传入参数：{}", dto);
        MinerSettings settings = getSettings();
        BigDecimal unitFee = settings.getElectricFee();
        LocalDateTime expiryLimit = LocalDateTime.now().minusDays(30);
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMiner::getUserId, userId)
                .eq(UserMiner::getNftBurnStatus, 1);
        switch (dto.getMode()) {
            case 1:
                wrapper.eq(UserMiner::getStatus, 0)
                        .eq(UserMiner::getMinerType, dto.getMinerType());
                applyQuantityLimit(wrapper, dto.getQuantity());
                break;
            case 2:
                LocalDateTime targetDate = expiryLimit.plusDays(dto.getDays() == null ? 30 : dto.getDays());
                wrapper.eq(UserMiner::getStatus, 1)
                        .eq(UserMiner::getMinerType, dto.getMinerType())
                        .ge(UserMiner::getPaymentDate, expiryLimit)
                        .le(UserMiner::getPaymentDate, targetDate);
                applyQuantityLimit(wrapper, dto.getQuantity());
                break;
            case 3:
                wrapper.eq(UserMiner::getStatus, 2)
                        .eq(UserMiner::getMinerType, dto.getMinerType());
                applyQuantityLimit(wrapper, dto.getQuantity());
                break;
            case 4:
                wrapper.eq(UserMiner::getStatus, 2);
                break;
            case 5:
                if (dto.getUserMinerIds() == null || dto.getUserMinerIds().isEmpty()) {
                    throw new BusinessException("请选择要缴纳电费的矿机");
                }
                wrapper.in(UserMiner::getId, dto.getUserMinerIds());
                break;
            case 6:
                break;
            default:
                throw new BusinessException("模式错误");
        }
        List<UserMiner> targets = this.list(wrapper);
        if (targets.isEmpty()) {
            throw new BusinessException("没有符合条件的矿机，请先确认卡牌销毁已完成");
        }
        BigDecimal totalFee = unitFee.multiply(new BigDecimal(targets.size()));
        if (userMapper.updateBalanceAtomic(userId, totalFee.negate()) == 0) {
            throw new BusinessException("余额不足，需支付: " + totalFee);
        }
        userBillServe.createBillAndUpdateBalance(
                userId,
                totalFee,
                BillType.PLATFORM,
                FundType.EXPENSE,
                TransactionType.PURCHASE,
                "缴纳电费-模式" + dto.getMode(),
                null,
                null,
                null,
                0,
                null
        );

        List<Long> targetIds = targets.stream().map(UserMiner::getId).collect(Collectors.toList());
        this.lambdaUpdate()
                .in(UserMiner::getId, targetIds)
                .set(UserMiner::getIsElectricityPaid, 1)
                .setSql("status = 1")
                .setSql("payment_date = CASE " +
                        "WHEN payment_date IS NULL OR payment_date < DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                        "THEN NOW() " +
                        "ELSE DATE_ADD(payment_date, INTERVAL 30 DAY) END")
                .setSql("eligible_date = NULL")
                .update();
    }

    private void applyQuantityLimit(LambdaQueryWrapper<UserMiner> wrapper, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            return;
        }
        long totalCount = this.count(wrapper);
        int limit = Math.min(quantity, (int) totalCount);
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
    }

    /**
     * 矿机每日收益
     * @throws Exception
     */
    @Override
    public void processDailyProfit() throws Exception {
        log.info(">>> 开始执行每日收益分发任务...");
        MinerSettings settings = getSettings();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryLimit = now.minusDays(30);
        LocalDateTime todayStart = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
        BigInteger unitDivisor = BigInteger.valueOf(10).pow(15);
        int totalHandledMiners = 0;

        while (true) {
            List<UserMiner> activeMiners = this.list(new LambdaQueryWrapper<UserMiner>()
                    .eq(UserMiner::getNftBurnStatus, 1)
                    .eq(UserMiner::getStatus, 1)
                    .ge(UserMiner::getPaymentDate, expiryLimit)
                    .and(w -> w.isNull(UserMiner::getLastRewardTime)
                            .or()
                            .lt(UserMiner::getLastRewardTime, todayStart))
            );
            if (activeMiners.isEmpty()) {
                log.info("<<< 每日收益分发任务执行完毕，共处理矿机：{} 台", totalHandledMiners);
                break;
            }
            Map<String, List<UserMiner>> groupedByWallet = activeMiners.stream()
                    .collect(Collectors.groupingBy(UserMiner::getWalletAddress));
            List<String> walletAddresses = new ArrayList<>(groupedByWallet.keySet());
            int maxUsersPerTx = 10;
            for (int i = 0; i < walletAddresses.size(); i += maxUsersPerTx) {
                int end = Math.min(i + maxUsersPerTx, walletAddresses.size());
                List<String> subWallets = walletAddresses.subList(i, end);

                // 1. 预处理数据并提前插入数据库（状态为未发放），缩短事务持锁时间
                List<MinerProfitRecord> allRecordsInBatch = new ArrayList<>();
                List<String> tos = new ArrayList<>();
                List<AionContract.BatchData> dataList = new ArrayList<>();
                long timeSuffix = System.currentTimeMillis() % 1000000;

                transactionTemplate.execute(status -> {
                    for (String wallet : subWallets) {
                        List<UserMiner> userMiners = groupedByWallet.get(wallet);
                        Map<String, List<UserMiner>> byType = userMiners.stream()
                                .collect(Collectors.groupingBy(UserMiner::getMinerType));
                        BigInteger[] slotAmounts = new BigInteger[4];
                        Arrays.fill(slotAmounts, BigInteger.ZERO);
                        Long userOrderId = null;

                        for (int typeIdx = 0; typeIdx <= 3; typeIdx++) {
                            String mType = String.valueOf(typeIdx);
                            if (byType.containsKey(mType)) {
                                List<UserMiner> minersOfThisType = byType.get(mType);
                                BigDecimal typeAmount = normalizeMinerDailyProfit(settings.getMinerDailyProfitByType(mType));
                                List<MinerProfitRecord> groupRecords = minersOfThisType.stream().map(m -> {
                                    MinerProfitRecord r = new MinerProfitRecord();
                                    r.setUserId(m.getUserId());
                                    r.setWalletAddress(m.getWalletAddress());
                                    r.setAmount(typeAmount);
                                    r.setMinerType(m.getMinerType());
                                    r.setMinerId(String.valueOf(m.getId()));
                                    r.setLockType("3".equals(m.getMinerType()) ? 0 : Integer.parseInt(m.getMinerType()) + 1);
                                    r.setDistType("3".equals(m.getMinerType()) ? 2 : 1);
                                    r.setPayoutStatus(0); // 初始为未支付
                                    r.setStatus(1);
                                    return r;
                                }).collect(Collectors.toList());
                                profitRecordService.saveBatch(groupRecords);
                                if (userOrderId == null) {
                                    userOrderId = Long.parseLong(groupRecords.get(0).getId() + "" + timeSuffix);
                                }
                                for (MinerProfitRecord r : groupRecords) {
                                    r.setActualOrderId(userOrderId);
                                }
                                profitRecordService.updateBatchById(groupRecords);
                                allRecordsInBatch.addAll(groupRecords);
                                BigInteger groupTotalWei = toWei(typeAmount).multiply(BigInteger.valueOf(minersOfThisType.size()));
                                slotAmounts[typeIdx] = groupTotalWei.divide(unitDivisor);
                            }
                        }
                        tos.add(wallet);
                        dataList.add(new AionContract.BatchData(
                                BigInteger.valueOf(userOrderId),
                                slotAmounts[0],
                                slotAmounts[1],
                                slotAmounts[2],
                                slotAmounts[3]
                        ));
                    }
                    return true;
                });

                // 2. 在事务外部执行链上调用（核心修复：避免长事务持锁导致死锁）
                TransactionReceipt receipt = null;
                try {
                    log.info("执行链上分发交易：用户数={}, 记录数={}", tos.size(), allRecordsInBatch.size());
                    receipt = aionContract.allocateEmissionToLocksBatch(tos, dataList);
                } catch (Exception e) {
                    log.error("链上分发接口调用异常", e);
                }

                // 3. 根据链上结果，开启第二个短事务更新记录状态
                if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                    final TransactionReceipt finalReceipt = receipt;
                    transactionTemplate.execute(status -> {
                        List<Long> ids = allRecordsInBatch.stream().map(MinerProfitRecord::getId).collect(Collectors.toList());
                        profitRecordService.lambdaUpdate()
                                .in(MinerProfitRecord::getId, ids)
                                .set(MinerProfitRecord::getPayoutStatus, 1)
                                .set(MinerProfitRecord::getPayoutTime, LocalDateTime.now())
                                .set(MinerProfitRecord::getTxId, finalReceipt.getTransactionHash())
                                .update();

                        List<Long> minerIds = allRecordsInBatch.stream()
                                .map(r -> Long.parseLong(r.getMinerId()))
                                .distinct()
                                .collect(Collectors.toList());
                        this.lambdaUpdate()
                                .in(UserMiner::getId, minerIds)
                                .set(UserMiner::getLastRewardTime, now)
                                .update();
                        return true;
                    });
                } else {
                    log.error("链上交易失败或收据为空，记录保留为待处理状态。");
                }
            }
            totalHandledMiners += activeMiners.size();
        }
    }

    /**
     * 管理员代领取代币 (CLAIM)
     */
    @Override
    public String adminClaimAll(GetAdminClaimVO dto) throws Exception {
        Long orderId = IdWorker.getId();
        Long userId = getFormalUserId();
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(0, "用户不存在");
        }
        try {
            TransactionReceipt receipt = aionContract.claimAll(user.getUserWalletAddress(), dto.getLockType(), BigInteger.valueOf(orderId));
            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                AionContract.OrderRecord order = aionContract.getOrder(user.getUserWalletAddress(), BigInteger.valueOf(orderId));
                BigDecimal netAmount = new BigDecimal(order.getNetAmount())
                        .movePointLeft(18)
                        .setScale(6, RoundingMode.DOWN);

                userBillServe.createBillAndUpdateBalance(
                        userId,
                        netAmount,
                        BillType.ON_CHAIN,
                        FundType.INCOME,
                        TransactionType.PROFIT,
                        "收益领取成功(已按仓位销毁部分额度)",
                        orderId.toString(),
                        receipt.getTransactionHash(),
                        JSON.toJSONString(receipt),
                        0,
                        null
                );
                return receipt.getTransactionHash();
            }
            throw new BusinessException("链上领取执行失败");
        } catch (AionContractException e) {
            log.error("链上领取执行失败: {}", e.getDecodedDetail());
            throw new BusinessException("链上领取执行失败: " + e.getErrorMessage());
        } catch (BusinessException e) {
            log.error("链上领取执行失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("链上领取执行失败，关键参数： error={}", e);
            throw new BusinessException("链上领取执行失败，请检查账单日志");
        }
    }

    /**
     * 管理员代兑换未解锁碎片
     */
    @Override
    public String adminExchangeLocked(AdminMinerActionDTO dto) throws Exception {
        Long userId = getFormalUserId();
        User user = userService.getById(userId);
        if (user != null) {
            dto.setAddress(user.getUserWalletAddress());
        }
        log.info("管理员代兑换未解锁碎片 请求参数：{}", dto);
        // 将前端传来的正常数量转换为链上18位精度的 BigInteger
        BigInteger chainAmount = dto.getAmount()
                .multiply(new BigDecimal("1000000000000000000"))
                .toBigInteger();
        Long orderId = IdWorker.getId();
        try {
            TransactionReceipt receipt = aionContract.exchangeLockedFragment(
                    dto.getAddress(),
                    dto.getLockType(),
                    chainAmount,
                    BigInteger.valueOf(orderId)
            );
            log.info("管理员代兑换未解锁碎片 链上返回数据：{}", receipt);
            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                AionContract.OrderRecord order = aionContract.getOrder(dto.getAddress(), BigInteger.valueOf(orderId));
                log.info("兑换未解锁碎片获取订单详情：{}", order.getExecutedAmount().toString());
                CreateUserBillVO vo = new CreateUserBillVO();
                vo.setNum(order.getExecutedAmount().toString());
                userBillServe.createBillAndUpdateBalance(
                        userId,
                        BigDecimal.ZERO,
                        BillType.FRAGMENT,
                        FundType.INCOME,
                        TransactionType.EXCHANGE,
                        "收益兑换碎片(未解锁部分)",
                        orderId.toString(),
                        receipt.getTransactionHash(),
                        JSON.toJSONString(receipt),
                        0,
                        vo
                );
                return receipt.getTransactionHash();
            }
            log.info("未解锁碎片兑换执行失败");
            throw new BusinessException("未解锁碎片兑换执行失败");
        } catch (AionContractException e) {
            log.error("兑换未解锁碎片失败: {}", e.getDecodedDetail());
            throw new BusinessException("兑换失败: " + e.getErrorMessage());
        } catch (BusinessException e) {
            log.error("兑换未解锁碎片业务逻辑失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("创建账单系统异常，关键参数：address={}, error={}", dto.getAddress(), e.getMessage(), e);
            throw new BusinessException("系统处理失败，请检查账单日志");
        }
    }

    /**
     * 管理员代兑换已解锁碎片
     */
    @Override
    public String adminExchangeUnlocked(AdminMinerActionDTO dto) throws Exception {
        log.info("管理员代兑换已解锁碎片 请求参数：{}", dto);
        Long userId = getFormalUserId();
        User user = userService.getById(userId);
        if (user != null) {
            dto.setAddress(user.getUserWalletAddress());
        }
        // 将前端传来的正常数量转换为链上18位精度的 BigInteger
        BigInteger chainAmount = dto.getAmount()
                .multiply(new BigDecimal("1000000000000000000"))
                .toBigInteger();
        try {
            Long orderId = IdWorker.getId();
            TransactionReceipt receipt = aionContract.exchangeUnlockedFragment(
                    dto.getAddress(),
                    dto.getLockType(),
                    chainAmount,
                    BigInteger.valueOf(orderId)
            );
            log.info("管理员代兑换已解锁碎片 链上返回数据：{}", receipt);
            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                AionContract.OrderRecord order = aionContract.getOrder(dto.getAddress(), BigInteger.valueOf(orderId));
                log.info("兑换已解锁碎片获取订单详情：{}", order.getExecutedAmount().toString());
                CreateUserBillVO vo = new CreateUserBillVO();
                vo.setNum(order.getExecutedAmount().toString());
                userBillServe.createBillAndUpdateBalance(
                        userId,
                        BigDecimal.ZERO,
                        BillType.FRAGMENT,
                        FundType.INCOME,
                        TransactionType.EXCHANGE,
                        "收益兑换碎片(已解锁部分)",
                        orderId.toString(),
                        receipt.getTransactionHash(),
                        JSON.toJSONString(receipt),
                        0,
                        vo
                );
                return receipt.getTransactionHash();
            }
            log.info("已解锁碎片兑换执行失败");
            throw new BusinessException("已解锁碎片兑换执行失败");
        } catch (AionContractException e) {
            log.error("兑换已解锁碎片失败: {}", e.getDecodedDetail());
            throw new BusinessException("兑换失败: " + e.getErrorMessage());
        } catch (BusinessException e) {
            log.error("兑换已解锁碎片业务逻辑失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("创建账单系统异常，关键参数：address={}, error={}", dto.getAddress(), e.getMessage(), e);
            throw new BusinessException("系统处理失败，请检查账单日志");
        }
    }

    /**
     * 碎片兑换卡牌 NFT
     */
    @Override
    public void buyNftWithFragments(Long userId, FragmentExchangeNftDTO dto) throws Exception {
        // 新增卡牌ID参数，避免默认卡牌导致分发错误
        if (dto.getCardId() == null) {
            throw new BusinessException("卡牌ID不能为空");
        }
        MinerSettings settings = getSettings();
        BigDecimal rate = settings.getFragmentToCardRateByCardId(dto.getCardId());
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("碎片换卡比例未配置，请联系系统管理员");
        }
        BigDecimal totalNeed = rate.multiply(new BigDecimal(dto.getQuantity()));
        String rawFragmentNum = totalNeed.movePointRight(18).toBigInteger().toString();
        User user = userMapper.selectById(userId);
        CreateUserBillVO vo = new CreateUserBillVO();
        vo.setNum(rawFragmentNum);
        vo.setCardId(dto.getCardId());
        userBillServe.createBillAndUpdateBalance(
                userId,
                BigDecimal.ZERO,
                BillType.FRAGMENT,
                FundType.EXPENSE,
                TransactionType.EXCHANGE,
                "碎片兑换NFT卡牌 x" + dto.getQuantity(),
                "FRAG_REDEEM_" + System.currentTimeMillis(),
                null,
                null,
                0,
                vo
        );
        // 执行链上分发 NFT
        TransactionReceipt receipt = cardNftContract.distribute(
                user.getUserWalletAddress(),
                BigInteger.valueOf(dto.getCardId()),
                BigInteger.valueOf(dto.getQuantity())
        );
        if (receipt == null || !"0x1".equalsIgnoreCase(receipt.getStatus())) {
            throw new BusinessException("链上发放卡牌失败，请稍后重试");
        }
    }

    /**
     * 每日电费分成结算
     * 规则：
     * 1. 统计直属下级持有的活跃矿机总数决定比例
     * 2. 奖金基数：若只有1个下级缴费则按该笔预计，若多于1个则扣除最高的一笔
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
            // 统计直属下级的活跃机器总数（用于定比例阶梯）
            parentSubActiveCountMap.merge(parentId, 1, Integer::sum);
            // 统计今日缴费情况（用于算奖金基数）
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
            // 确定比例：基于该上级的【所有直属下级活跃机器总数】
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
            // 计算基数：1人缴费不剔除，多人缴费剔除最高一项
            BigDecimal bonusBase;
            if (fees.size() == 1) {
                bonusBase = fees.get(0);
            } else {
                BigDecimal maxFee = Collections.max(fees);
                BigDecimal totalFee = fees.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                bonusBase = totalFee.subtract(maxFee);
            }
            // 执行发放
            if (bonusBase.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal rewardAmount = bonusBase.multiply(ratio);
                try {
                    userBillServe.createBillAndUpdateBalance(
                            parentId, rewardAmount, BillType.PLATFORM, FundType.INCOME,
                            TransactionType.REWARD,
                            String.format("直属下级电费分成(下级总机:%d, 比例:%.2f%%, 缴费人数:%d)",
                                    totalSubMiners, ratio.multiply(new BigDecimal("100")), fees.size()),
                            null, null, null, 0, null
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

    /**
     * 获取当前登录的正式用户ID
     * @return 成功则返回Long类型的UserId，如果是临时会话或未登录则抛出异常
     */
    private Long getFormalUserId() {
        StpUtil.checkLogin();
        String loginId = StpUtil.getLoginIdAsString();
        if (!StrUtil.isNumeric(loginId)) {
            log.warn("检测到临时会话访问受限接口: {}", loginId);
            throw new BusinessException("请先完成登录或注册");
        }
        return Long.parseLong(loginId);
    }

    /**
     * 获取系统设置
     */
    private BigDecimal normalizeMinerDailyProfit(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(6, RoundingMode.DOWN);
        }
        return amount.setScale(6, RoundingMode.DOWN);
    }

    private BigInteger toWei(BigDecimal amount) {
        return normalizeMinerDailyProfit(amount)
                .movePointRight(18)
                .toBigInteger();
    }

    private MinerSettings getSettings() {
        SystemConfig config = configMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "MINER_SYSTEM_SETTINGS"));
        if (config == null) return new MinerSettings();
        return com.alibaba.fastjson2.JSON.parseObject(config.getConfigValue(), MinerSettings.class);
    }
}
