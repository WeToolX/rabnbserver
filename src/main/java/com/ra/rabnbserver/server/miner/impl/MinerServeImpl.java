package com.ra.rabnbserver.server.miner.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.CreateUserBillVO;
import com.ra.rabnbserver.VO.MinerSettings;
import com.ra.rabnbserver.contract.AionContract;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.dto.MinerAccelerationDTO;
import com.ra.rabnbserver.dto.MinerElectricityDTO;
import com.ra.rabnbserver.dto.MinerQueryDTO;
import com.ra.rabnbserver.dto.adminMinerAction.AdminMinerActionDTO;
import com.ra.rabnbserver.dto.adminMinerAction.FragmentExchangeNftDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.AionContractException;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.MinerProfitRecordMapper;
import com.ra.rabnbserver.mapper.SystemConfigMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.mapper.UserMinerMapper;
import com.ra.rabnbserver.model.ApiResponse;
import com.ra.rabnbserver.pojo.*;
import com.ra.rabnbserver.server.miner.MinerServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

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
    private final IService<MinerProfitRecord> profitRecordService;

    private final AionContract aionContract;

    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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

        if (query.getExpiryStatus() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiredLine = now.minusDays(30); // 30天前的时间点
            LocalDateTime warningLine = now.minusDays(25); // 25天前的时间点（即距离过期还有5天）

            switch (query.getExpiryStatus()) {
                case 1: // 已到期：paymentDate < 30天前，或者从未缴费(status=0/paymentDate is null)
                    wrapper.and(w -> w.lt(UserMiner::getPaymentDate, expiredLine)
                            .or().isNull(UserMiner::getPaymentDate)
                            .or().eq(UserMiner::getStatus, 0));
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

    /**
     * 购买矿机（销毁卡牌）
     * @param userId 用户id
     * @param minerType "0" ;//小形矿机，"1"  //中形矿机，"2" //大形矿机，"3"//特殊矿机
     * @param quantity 购买数量
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyMinerBatch(Long userId, String minerType, int quantity) {
        if (quantity <= 0) throw new BusinessException("数量不合法");
        User user = userMapper.selectById(userId);
        if(user == null){
            throw new BusinessException(0,"用户不存在");
        }
        String walletAddress = user.getUserWalletAddress();

        // 异常框架预检查
        purchaseRetryServe.checkUserErr(walletAddress);

        // 合约前置检查：必须检查后端钱包是否获得用户授权
        try {
//            String adminWallet = cardNftContract.getAddress();
//            Boolean isApproved = cardNftContract.isApprovedForAll(walletAddress, adminWallet);
//            if (isApproved == null || !isApproved) {
//                throw new BusinessException("检测到未授权卡牌操作，请先在页面完成授权");
//            }
            BigInteger balance = cardNftContract.balanceOf(walletAddress);
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
            miner.setStatus(0);
            miner.setEligibleDate(LocalDateTime.now().plusDays(15));
            this.save(miner);
            createdMiners.add(miner);
        }
        // 执行销毁逻辑（遍历执行，失败则进入重试框架）
        for (UserMiner miner : createdMiners) {
            try {
                TransactionReceipt receipt = cardNftContract.burnUser(walletAddress, BigInteger.ONE);
                // 合约调用未抛异常且 receipt 不为空，视为成功
                if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                    boolean success = this.lambdaUpdate()
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
        log.info("缴纳电费传入参数：{}",dto.toString());
        MinerSettings settings = getSettings();
        BigDecimal unitFee = settings.getElectricFee();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryLimit = now.minusDays(30);
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMiner::getUserId, userId);
        //只有卡牌销毁成功(nftBurnStatus=1)的矿机，才允许下一步操作
        wrapper.eq(UserMiner::getNftBurnStatus, 1);
        switch (dto.getMode()) {
            case 1: // 初始激活缴费  有数量就传入数量，数量为0则全部
                wrapper.eq(UserMiner::getStatus, 0).eq(UserMiner::getMinerType, dto.getMinerType());
                if (dto.getQuantity() != null && !dto.getQuantity().equals("0")) {
                    //wrapper.last("LIMIT " + dto.getQuantity());
                    int quantity = dto.getQuantity();
                    long totalCount = this.count(wrapper);
                    // 限制数量不能超过实际记录数
                    int limit = Math.min(quantity, (int) totalCount);
                    if (limit > 0) {
                        wrapper.last("LIMIT " + limit);
                    }
                }
                break;
            case 2: // 续费
                LocalDateTime targetDate = expiryLimit.plusDays(dto.getDays());
                wrapper.eq(UserMiner::getStatus, 1).eq(UserMiner::getMinerType, dto.getMinerType())
                        .ge(UserMiner::getPaymentDate, expiryLimit).le(UserMiner::getPaymentDate, targetDate);
                if (dto.getQuantity() != null && !dto.getQuantity().equals("0")) {
                    //wrapper.last("LIMIT " + dto.getQuantity());
                    int quantity = dto.getQuantity();
                    long totalCount = this.count(wrapper);
                    // 限制数量不能超过实际记录数
                    int limit = Math.min(quantity, (int) totalCount);
                    if (limit > 0) {
                        wrapper.last("LIMIT " + limit);
                    }
                }
                break;
            case 3: // 已到期 (按类型) 有数量就传入数量，数量为0则全部
                wrapper.eq(UserMiner::getMinerType, dto.getMinerType())
                        .and(w -> w.lt(UserMiner::getPaymentDate, expiryLimit).or().eq(UserMiner::getStatus, 0));
                if (dto.getQuantity() != null && !dto.getQuantity().equals("0")) {
                    //wrapper.last("LIMIT " + dto.getQuantity());
                    int quantity = dto.getQuantity();
                    long totalCount = this.count(wrapper);
                    // 限制数量不能超过实际记录数
                    int limit = Math.min(quantity, (int) totalCount);
                    if (limit > 0) {
                        wrapper.last("LIMIT " + limit);
                    }
                }
                break;
            case 4: // 全部已到期/未激活
                wrapper.and(w -> w.lt(UserMiner::getPaymentDate, expiryLimit).or().eq(UserMiner::getStatus, 0));
                break;
            case 5: // 新增：指定矿机ID缴费
                if (dto.getUserMinerIds() == null || dto.getUserMinerIds().isEmpty()) {
                    throw new BusinessException("请选择要缴纳电费的矿机");
                }
                wrapper.in(UserMiner::getId, dto.getUserMinerIds());
                break;
            case 6: // 一键缴纳所有矿机电费
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
                TransactionType.PURCHASE, "缴纳电费-模式" + dto.getMode(), null, null, null, 0,null);

        List<Long> targetIds = targets.stream().map(UserMiner::getId).collect(Collectors.toList());

        // 执行原子更新
        this.lambdaUpdate()
                .in(UserMiner::getId, targetIds)
                //.set(UserMiner::getPaymentDate, now) // 统一更新缴费时间
                .set(UserMiner::getIsElectricityPaid, 1)
                /*
                   使用 setSql 实现原子逻辑：
                   如果当前状态是 0 (待激活)，则更新为 1 (运行中)；
                   如果当前状态已经是 1 (续费情况)，则保持 1。
                */
                .setSql("status = CASE WHEN status = 0 THEN 1 ELSE status END")
                /*
                  使用 MySQL DATE_ADD 函数实现叠加：
                  1. 如果 payment_date 为空，或者已经超过30天(已过期)，则设置为 NOW()
                  2. 如果还在有效期内，则在原有 payment_date 基础上增加 30 天
                */
                .setSql("payment_date = CASE " +
                        "WHEN payment_date IS NULL OR payment_date < DATE_SUB(NOW(), INTERVAL 30 DAY) " +
                        "THEN NOW() " +
                        "ELSE DATE_ADD(payment_date, INTERVAL 30 DAY) END")
                .update();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyAccelerationPack(Long userId, MinerAccelerationDTO dto) {
        MinerSettings settings = getSettings();
        BigDecimal unitFee = settings.getAccelerationFee();
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<UserMiner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserMiner::getUserId, userId)
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
        // 获取目标 ID 列表
        List<Long> targetIds = targets.stream().map(UserMiner::getId).collect(Collectors.toList());
        // 执行原子更新
        this.lambdaUpdate()
                .in(UserMiner::getId, targetIds)
                .eq(UserMiner::getIsAccelerated, 0) // 原子性检查：确保未加速过
                .set(UserMiner::getIsAccelerated, 1)
                /*
                   使用 MySQL 函数实现原子逻辑：
                   DATE_ADD(create_time, INTERVAL 3 DAY) 计算限制日期
                   如果限制日期在当前时间之后，则设为限制日期；否则设为当前时间。
                */
                .setSql("eligible_date = CASE " +
                        "WHEN DATE_ADD(create_time, INTERVAL 3 DAY) > NOW() " +
                        "THEN DATE_ADD(create_time, INTERVAL 3 DAY) " +
                        "ELSE NOW() END")
                .update();
        userBillServe.createBillAndUpdateBalance(userId, totalFee, BillType.PLATFORM, FundType.EXPENSE,
                TransactionType.PURCHASE, "购买加速包-模式" + dto.getMode(), null, null, null, 0,null);
    }

    @Override
    public void processDailyProfit() throws Exception {
        // 1. 链上前置校验与结算
        aionContract.settleToCurrentYear();
        BigInteger todayMintableWei = aionContract.getTodayMintable();
        if (todayMintableWei.compareTo(BigInteger.ZERO) <= 0) return;

        // 2. 筛选符合条件的活跃矿机
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiryLimit = now.minusDays(30);
        List<UserMiner> activeMiners = this.list(new LambdaQueryWrapper<UserMiner>()
                .eq(UserMiner::getNftBurnStatus, 1)
                .eq(UserMiner::getStatus, 1)
                .ge(UserMiner::getPaymentDate, expiryLimit)
                .le(UserMiner::getEligibleDate, now));
        if (activeMiners.isEmpty()) return;

        // 3. 计算单台收益 (基于全量已销毁卡牌的矿机总数)
        List<UserMiner> allMiners = this.list(new LambdaQueryWrapper<UserMiner>().eq(UserMiner::getNftBurnStatus, 1));
        BigInteger perMinerAmountWei = todayMintableWei.divide(BigInteger.valueOf(allMiners.size()));

        // 4. 按钱包地址聚合用户（每个地址对应 4 个槽位数据）
        Map<String, List<UserMiner>> groupedByWallet = activeMiners.stream()
                .collect(Collectors.groupingBy(UserMiner::getWalletAddress));

        List<String> walletAddresses = new ArrayList<>(groupedByWallet.keySet());
        int maxUsersPerTx = 100; // 每一批次处理 100 个用户（对应 tos 长度）50
        BigInteger unitDivisor = BigInteger.valueOf(10).pow(15); // Wei(18位) -> 3位精度整数 (除以 10^15)

        for (int i = 0; i < walletAddresses.size(); i += maxUsersPerTx) {
            int end = Math.min(i + maxUsersPerTx, walletAddresses.size());
            List<String> subWallets = walletAddresses.subList(i, end);

            transactionTemplate.execute(status -> {
                try {
                    List<MinerProfitRecord> allRecordsInBatch = new ArrayList<>();
                    List<String> tos = new ArrayList<>();
                    List<AionContract.BatchData> dataList = new ArrayList<>();
                    long timeSuffix = System.currentTimeMillis() % 1000000;

                    for (String wallet : subWallets) {
                        List<UserMiner> userMiners = groupedByWallet.get(wallet);
                        Map<String, List<UserMiner>> byType = userMiners.stream()
                                .collect(Collectors.groupingBy(UserMiner::getMinerType));

                        // 定义四个槽位的金额（初始化为0，实现自动占位）
                        BigInteger[] slotAmounts = new BigInteger[4]; // 0:L1, 1:L2, 2:L3, 3:Direct
                        Arrays.fill(slotAmounts, BigInteger.ZERO);
                        Long userOrderId = null;

                        // 遍历可能的矿机类型：0, 1, 2, 3
                        for (int typeIdx = 0; typeIdx <= 3; typeIdx++) {
                            String mType = String.valueOf(typeIdx);
                            if (byType.containsKey(mType)) {
                                List<UserMiner> minersOfThisType = byType.get(mType);

                                // A. 数据库明细处理
                                List<MinerProfitRecord> groupRecords = minersOfThisType.stream().map(m -> {
                                    MinerProfitRecord r = new MinerProfitRecord();
                                    r.setUserId(m.getUserId());
                                    r.setWalletAddress(m.getWalletAddress());
                                    r.setAmount(new BigDecimal(perMinerAmountWei));
                                    r.setMinerType(m.getMinerType());
                                    r.setMinerId(String.valueOf(m.getId()));
                                    // 链上仓位映射：3型->0(Direct), 0/1/2型->1/2/3(Lock)
                                    r.setLockType("3".equals(m.getMinerType()) ? 0 : Integer.parseInt(m.getMinerType()) + 1);
                                    r.setDistType("3".equals(m.getMinerType()) ? 2 : 1);
                                    r.setPayoutStatus(0);
                                    r.setStatus(1);
                                    return r;
                                }).collect(Collectors.toList());

                                profitRecordService.saveBatch(groupRecords);

                                // B. 生成业务订单号并持久化（每用户一个 orderId）
                                if (userOrderId == null) {
                                    userOrderId = Long.parseLong(groupRecords.get(0).getId() + "" + timeSuffix);
                                }
                                for (MinerProfitRecord r : groupRecords) {
                                    r.setActualOrderId(userOrderId);
                                }
                                profitRecordService.updateBatchById(groupRecords);
                                allRecordsInBatch.addAll(groupRecords);

                                // C. 准备链上参数
                                // 计算该类型总额并转换为3位精度整数
                                BigInteger groupTotalWei = perMinerAmountWei.multiply(BigInteger.valueOf(minersOfThisType.size()));
                                BigInteger chainAmount = groupTotalWei.divide(unitDivisor);

                                slotAmounts[typeIdx] = chainAmount;
                            }
                        }

                        // 将用户地址和对应的 BatchData 放入列表（下标严格对应）
                        BigInteger chainOrderId = userOrderId == null ? BigInteger.ZERO : BigInteger.valueOf(userOrderId);
                        tos.add(wallet);
                        dataList.add(new AionContract.BatchData(
                                chainOrderId,
                                slotAmounts[0], // L1
                                slotAmounts[1], // L2
                                slotAmounts[2], // L3
                                slotAmounts[3]  // Direct
                        ));
                    }

                    // 5. 调用链上批量分发接口
                    log.info("执行链上批量分发：聚合地址数={}, 记录总笔数={}", tos.size(), allRecordsInBatch.size());
                    TransactionReceipt receipt = aionContract.allocateEmissionToLocksBatch(tos, dataList);

                    if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                        List<Long> ids = allRecordsInBatch.stream().map(MinerProfitRecord::getId).collect(Collectors.toList());
                        profitRecordService.lambdaUpdate()
                                .in(MinerProfitRecord::getId, ids)
                                .set(MinerProfitRecord::getPayoutStatus, 1)
                                .set(MinerProfitRecord::getPayoutTime, LocalDateTime.now())
                                .set(MinerProfitRecord::getTxId, receipt.getTransactionHash())
                                .update();
                        return true;
                    } else {
                        status.setRollbackOnly();
                        return false;
                    }
                } catch (Exception e) {
                    log.error("收益分发批次异常，执行回滚", e);
                    status.setRollbackOnly();
                    return false;
                }
            });
        }
    }


    /**
     * 管理员代领取代币 (CLAIM)
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String adminClaimAll(AdminMinerActionDTO dto) throws Exception {
        try {
            TransactionReceipt receipt = aionContract.claimAll(dto.getAddress(), dto.getLockType(), BigInteger.valueOf(dto.getOrderId()));
            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                AionContract.OrderRecord order = aionContract.getOrder(dto.getAddress(), BigInteger.valueOf(dto.getOrderId()));
                Long userId = getUserIdByAddress(dto.getAddress());

                userBillServe.createBillAndUpdateBalance(
                        userId,
                        new BigDecimal(order.getNetAmount()),
                        BillType.ON_CHAIN,
                        FundType.INCOME,
                        TransactionType.PROFIT,
                        "收益领取成功(已按仓位销毁部分额度)",
                        dto.getOrderId().toString(),
                        receipt.getTransactionHash(),
                        JSON.toJSONString(receipt),
                        0,
                        null
                );
                return receipt.getTransactionHash();
            }
            throw new BusinessException("链上领取执行失败");
        } catch (AionContractException e) {
            log.error("管理员领取失败: {}", e.getDecodedDetail());
            throw new BusinessException("链上领取失败: " + e.getErrorMessage());
        }
    }

    /**
     * 3. 管理员代兑换未解锁碎片
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String adminExchangeLocked(AdminMinerActionDTO dto) throws Exception {
        try {
            TransactionReceipt receipt = aionContract.exchangeLockedFragment(
                    dto.getAddress(), dto.getLockType(), dto.getAmount().toBigInteger(), BigInteger.valueOf(dto.getOrderId()));

            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                AionContract.OrderRecord order = aionContract.getOrder(dto.getAddress(), BigInteger.valueOf(dto.getOrderId()));
                Long userId = getUserIdByAddress(dto.getAddress());

                CreateUserBillVO vo = new CreateUserBillVO();
                vo.setNum(order.getExecutedAmount().toString());

                userBillServe.createBillAndUpdateBalance(
                        userId,
                        BigDecimal.ZERO,
                        BillType.FRAGMENT,
                        FundType.INCOME,
                        TransactionType.EXCHANGE,
                        "收益兑换碎片(未解锁部分)",
                        dto.getOrderId().toString(),
                        receipt.getTransactionHash(),
                        JSON.toJSONString(receipt),
                        0,
                        vo
                );
                return receipt.getTransactionHash();
            }
            throw new BusinessException("未解锁碎片兑换执行失败");
        } catch (AionContractException e) {
            log.error("兑换未解锁碎片失败: {}", e.getDecodedDetail());
            throw new BusinessException("兑换失败: " + e.getErrorMessage());
        }
    }

    /**
     * 4. 管理员代兑换已解锁碎片
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String adminExchangeUnlocked(AdminMinerActionDTO dto) throws Exception {
        try {
            TransactionReceipt receipt = aionContract.exchangeUnlockedFragment(
                    dto.getAddress(), dto.getLockType(), dto.getAmount().toBigInteger(), BigInteger.valueOf(dto.getOrderId()));

            if (receipt != null && "0x1".equalsIgnoreCase(receipt.getStatus())) {
                AionContract.OrderRecord order = aionContract.getOrder(dto.getAddress(), BigInteger.valueOf(dto.getOrderId()));
                Long userId = getUserIdByAddress(dto.getAddress());

                CreateUserBillVO vo = new CreateUserBillVO();
                vo.setNum(order.getExecutedAmount().toString());

                userBillServe.createBillAndUpdateBalance(
                        userId,
                        BigDecimal.ZERO,
                        BillType.FRAGMENT,
                        FundType.INCOME,
                        TransactionType.EXCHANGE,
                        "收益兑换碎片(已解锁部分)",
                        dto.getOrderId().toString(),
                        receipt.getTransactionHash(),
                        JSON.toJSONString(receipt),
                        0,
                        vo
                );
                return receipt.getTransactionHash();
            }
            throw new BusinessException("已解锁碎片兑换执行失败");
        } catch (AionContractException e) {
            log.error("兑换已解锁碎片失败: {}", e.getDecodedDetail());
            throw new BusinessException("兑换失败: " + e.getErrorMessage());
        }
    }

    /**
     * 5. 碎片兑换卡牌 NFT
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void buyNftWithFragments(Long userId, FragmentExchangeNftDTO dto) throws Exception {
        MinerSettings settings = getSettings();
        BigDecimal rate = settings.getFragmentToCardRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("碎片换卡比例未配置，请联系系统管理员");
        }

        BigDecimal totalNeed = rate.multiply(new BigDecimal(dto.getQuantity()));
        User user = userMapper.selectById(userId);

        CreateUserBillVO vo = new CreateUserBillVO();
        vo.setNum(totalNeed.toPlainString());

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

        TransactionReceipt receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(dto.getQuantity()));
        if (receipt == null || !"0x1".equalsIgnoreCase(receipt.getStatus())) {
            throw new BusinessException("链上发放卡牌失败，请稍后重试");
        }
    }

    private Long getUserIdByAddress(String address) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUserWalletAddress, address));
        return user != null ? user.getId() : 0L;
    }

    /**
     * 记录碎片兑换账单的通用逻辑
     * @param userId 用户ID
     * @param executedAmount 链上返回的实际执行数量（最小单位 BigInteger 转换后的 BigDecimal）
     * @param orderId 业务订单号
     * @param receipt 链上交易回执
     * @param isLocked 是否为未解锁兑换（用于备注区分）
     */
    private void recordFragmentExchangeBill(Long userId, BigDecimal executedAmount, Long orderId, TransactionReceipt receipt, boolean isLocked) {
        // 构造高精度数量参数对象
        // 因为碎片在数据库中是 String 类型存储以保证高精度，所以通过 VO 传递字符串
        CreateUserBillVO vo = new CreateUserBillVO();
        vo.setNum(executedAmount.toPlainString());
        // 调用账单服务
        userBillServe.createBillAndUpdateBalance(
                userId,                // 用户ID
                BigDecimal.ZERO,       // amount: 碎片账单的金额快照记为0（对应 UserBillServeImpl 193行逻辑）
                BillType.FRAGMENT,     // billType: 碎片账单类型
                FundType.INCOME,       // fundType: 收入（用户获得了碎片）
                TransactionType.EXCHANGE, // txType: 兑换业务
                isLocked ? "兑换未解锁收益为碎片" : "兑换已解锁收益为碎片", // remark
                String.valueOf(orderId), // orderId: 业务订单号
                receipt.getTransactionHash(), // txId: 链上哈希
                JSON.toJSONString(receipt),   // res: 链上响应回执
                0,                     // num: 传0，逻辑将优先使用 vo 中的字符串数量
                vo                     // 携带高精度数量的扩展对象
        );
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
