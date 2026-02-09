package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.AdminBillStatisticsVO;
import com.ra.rabnbserver.VO.CreateUserBillVO;
import com.ra.rabnbserver.VO.PaymentUsdtMetaVO;
import com.ra.rabnbserver.contract.CardNftContract;
import com.ra.rabnbserver.contract.PaymentUsdtContract;
import com.ra.rabnbserver.contract.support.AmountConvertUtils;
import com.ra.rabnbserver.dto.AdminBillQueryDTO;
import com.ra.rabnbserver.dto.BillQueryDTO;
import com.ra.rabnbserver.enums.*;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.ETFCard;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.card.EtfCardServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserBillServeImpl extends ServiceImpl<UserBillMapper, UserBill> implements UserBillServe {

    private final UserMapper userMapper;
    private final TransactionTemplate transactionTemplate;
    private final EtfCardServe etfCardServe;
    private final PaymentUsdtContract paymentUsdtContract;
    private final CardNftContract cardNftContract;
    private final UserBillRetryServeImpl billRetryServe;

    /**
     * 链上调用执行接口用于封装合约交互逻辑
     */
    @FunctionalInterface
    interface ChainCall {
        TransactionReceipt execute() throws Exception;
    }

    /**
     * 处理链上充值业务
     * 修改点：重构逻辑避免重复记账
     */
    @Override
    public void rechargeFromChain(Long userId, BigDecimal amount) throws Exception {
        User user = validateUserAndCheckLock(userId);
        String orderId = "0x" + IdWorker.get32UUID();
        BigInteger rawAmount = AmountConvertUtils.toRawAmount(AmountConvertUtils.Currency.USDT, amount);

        // 验证链上授权额度与余额
        checkChainAllowanceAndBalance(user.getUserWalletAddress(), rawAmount);

        TransactionReceipt receipt = null;
        String systemErrorMsg = null;

        // 1. 仅针对合约调用/网络请求进行 Try-Catch
        try {
            log.info("发起链上充值请求，用户：{}，金额：{}", user.getUserWalletAddress(), amount);
            receipt = paymentUsdtContract.deposit(orderId, user.getUserWalletAddress(), rawAmount);
        } catch (Exception e) {
            log.error("充值合约调用发生系统异常", e);
            systemErrorMsg = e.getMessage();
        }
        // 2. 统一处理结果（确保只执行一次记账操作）
        if (receipt != null && "0x1".equals(receipt.getStatus())) {
            // 情况 A: 链上执行成功
            createBillAndUpdateBalance(userId, amount, BillType.PLATFORM, FundType.INCOME,
                    TransactionType.DEPOSIT, "USDT充值成功", orderId, receipt.getTransactionHash(), JSON.toJSONString(receipt), 0,null);
            billRetryServe.ProcessingSuccessful(getBillIdByOrder(orderId));
        } else {
            // 情况 B: 链上执行失败 (receipt.status != 0x1) 或 系统异常 (systemErrorMsg != null)
            String finalRemark = (systemErrorMsg != null) ? "系统异常：" + systemErrorMsg : "链上合约执行失败";
            // 记录一笔零元账单用于异常框架追踪
            createBillAndUpdateBalance(userId, BigDecimal.ZERO, BillType.ERROR_ORDER, FundType.INCOME,
                    TransactionType.DEPOSIT, finalRemark, orderId, (receipt != null ? receipt.getTransactionHash() : null), null, 0,null);
            Long billId = getBillIdByOrder(orderId);
            updateBillByError(billId, TransactionStatus.FAILED, receipt, finalRemark);
            // 标记异常，注意此处传入钱包地址 user.getUserWalletAddress() 解决显示 "15" 的问题
            billRetryServe.markAbnormal(billId, user.getUserWalletAddress());
            // 抛出异常返回前端，此处已在 Try-Catch 外部，不会被再次拦截
            throw new BusinessException(finalRemark);
        }
    }

    /**
     * 处理购买NFT卡牌业务
     */
    @Override
    public void purchaseNftCard(Long userId, int quantity) {
        User user = validateUserAndCheckLock(userId);
        ETFCard currentBatch = etfCardServe.getActiveAndEnabledBatch();
        if (currentBatch == null) {
            throw new BusinessException("当前无可用卡牌批次");
        }

//        BigInteger remaining = syncChainInventory(currentBatch.getId());
//        if (remaining.compareTo(BigInteger.valueOf(quantity)) < 0) {
//            throw new BusinessException("链上库存不足，剩余数量：" + remaining);
//        }

        BigDecimal totalCost = currentBatch.getUnitPrice().multiply(new BigDecimal(quantity));
        String orderId = "NFT_BUY_" + IdWorker.getIdStr();

        transactionTemplate.executeWithoutResult(status -> {
            try {
                boolean stockOk = etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                        .eq(ETFCard::getId, currentBatch.getId())
                        .ge(ETFCard::getInventory, quantity)
                        .setSql("inventory = inventory - " + quantity)
                        .setSql("sold_count = sold_count + " + quantity));
                if (!stockOk) throw new BusinessException("本地库存不足");

                createBillAndUpdateBalance(userId, totalCost, BillType.PLATFORM, FundType.EXPENSE,
                        TransactionType.PURCHASE, "购买NFT卡牌 x" + quantity, orderId, null, null, quantity,null);
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });

        Long billId = getBillIdByOrder(orderId);
        TransactionReceipt receipt = null;
        String nftError = null;

        try {
            log.info("发起NFT链上分发，用户：{}，数量：{}", user.getUserWalletAddress(), quantity);
            receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(quantity));
        } catch (Exception e) {
            log.error("NFT分发执行异常", e);
            nftError = e.getMessage();
        }

        if (receipt != null && "0x1".equals(receipt.getStatus())) {
            updateBillReceipt(billId, receipt, "购买NFT卡牌成功 x" + quantity);
            billRetryServe.ProcessingSuccessful(billId);
        } else {
            String msg = (nftError != null) ? "分发异常：" + nftError : "分发合约执行失败";
            updateBillByError(billId, TransactionStatus.FAILED, receipt, msg);
            billRetryServe.markAbnormal(billId, user.getUserWalletAddress());
            if (nftError != null) throw new BusinessException(0, "卡牌分发处理中，请稍后查看");
        }
    }



    /**
     * 处理管理员分发NFT业务 - 增加库存校验与扣减
     */
    @Override
    public void distributeNftByAdmin(Long userId, Integer amount) {
        // 基础验证
        User user = validateUserAndCheckLock(userId);
        if (amount == null || amount <= 0) {
            throw new BusinessException("分发数量必须大于0");
        }
        // 获取当前可用批次并校验本地库存
        ETFCard currentBatch = etfCardServe.getActiveAndEnabledBatch();
        if (currentBatch == null) {
            throw new BusinessException("当前无可用卡牌批次，无法分发");
        }
        String orderId = "DIST_" + IdWorker.getIdStr();
        // 开启事务：扣减库存并创建初始账单
        transactionTemplate.executeWithoutResult(status -> {
            try {
                // 使用原子更新扣减库存，防止超卖/超发
                boolean stockOk = etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                        .eq(ETFCard::getId, currentBatch.getId())
                        .ge(ETFCard::getInventory, amount) // 检查剩余库存是否足够
                        .setSql("inventory = inventory - " + amount)
                        .setSql("sold_count = sold_count + " + amount));
                if (!stockOk) {
                    throw new BusinessException("本地库存不足，当前剩余：" + currentBatch.getInventory());
                }
                // 创建账单记录（分发通常是系统赠送，amount 传 0）
                createBillAndUpdateBalance(userId, BigDecimal.ZERO, BillType.ON_CHAIN, FundType.INCOME,
                        TransactionType.REWARD, "系统管理员手动分发 x" + amount, orderId, null, null, amount, null);
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
        // 异步/后续执行链上分发逻辑
        Long billId = getBillIdByOrder(orderId);
        TransactionReceipt receipt = null;
        String adminErr = null;
        try {
            log.info("管理员发起NFT链上分发，用户：{}，数量：{}", user.getUserWalletAddress(), amount);
            // 调用合约分发
            receipt = cardNftContract.distribute(user.getUserWalletAddress(), BigInteger.valueOf(amount));
        } catch (Exception e) {
            log.error("管理员分发链上执行异常", e);
            adminErr = e.getMessage();
        }
        // 更新最终状态
        if (receipt != null && "0x1".equals(receipt.getStatus())) {
            updateBillReceipt(billId, receipt, "系统管理员手动分发成功 x" + amount);
        } else {
            String msg = (adminErr != null) ? "分发指令已记录，但链上执行异常：" + adminErr : "管理员分发链上失败";
            updateBillByError(billId, TransactionStatus.FAILED, receipt, msg);
            // 标记异常，进入重试队列
            billRetryServe.markAbnormal(billId, user.getUserWalletAddress());
            throw new BusinessException(msg);
        }
    }

    private User validateUserAndCheckLock(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("用户不存在");
        billRetryServe.checkUserErr(String.valueOf(userId));
        return user;
    }

    private BigInteger syncChainInventory(Long batchId) {
        try {
            BigInteger remaining = cardNftContract.remainingMintable();
            etfCardServe.update(new LambdaUpdateWrapper<ETFCard>()
                    .eq(ETFCard::getId, batchId)
                    .set(ETFCard::getInventory, remaining.intValue()));
            return remaining;
        } catch (Exception e) {
            log.error("链上库存获取失败", e);
            throw new BusinessException("获取链上库存失败");
        }
    }

    private Long getBillIdByOrder(String orderId) {
        UserBill bill = this.getOne(new LambdaQueryWrapper<UserBill>().eq(UserBill::getTransactionOrderId, orderId));
        return bill != null ? bill.getId() : null;
    }

    private void updateBillStatus(Long billId, TransactionStatus status, String remark) {
        this.update(new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, billId)
                .set(UserBill::getStatus, status)
                .set(UserBill::getRemark, remark));
    }

    private void updateBillReceipt(Long billId, TransactionReceipt receipt, String remark) {
        this.update(new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, billId)
                .set(UserBill::getStatus, TransactionStatus.SUCCESS)
                .set(UserBill::getTxId, receipt.getTransactionHash())
                .set(UserBill::getChainResponse, JSON.toJSONString(receipt))
                .set(UserBill::getRemark, remark));
    }

    private void updateBillByError(Long billId, TransactionStatus status, TransactionReceipt receipt, String remark) {
        LambdaUpdateWrapper<UserBill> wrapper = new LambdaUpdateWrapper<UserBill>()
                .eq(UserBill::getId, billId)
                .set(UserBill::getStatus, status)
                .set(UserBill::getRemark, remark);
        if (receipt != null) {
            wrapper.set(UserBill::getTxId, receipt.getTransactionHash())
                    .set(UserBill::getChainResponse, JSON.toJSONString(receipt));
        }
        this.update(wrapper);
    }

    private void checkChainAllowanceAndBalance(String address, BigInteger amount) throws Exception {
        if (paymentUsdtContract.allowanceToPaymentUsdt(address).compareTo(amount) < 0) throw new BusinessException("授权额度不足");
        if (paymentUsdtContract.balanceOf(address).compareTo(amount) < 0) throw new BusinessException("链上余额不足");
    }

    /**
     * 统一创建账单并更新余额（支持平台余额与碎片余额）
     *
     * @param userId           用户ID
     * @param amount           变动金额（用于平台资金）
     * @param billType         账单类型（PLATFORM-平台余额, FRAGMENT-碎片, ON_CHAIN-链上, ERROR_ORDER-异常）
     * @param fundType         资金流向（INCOME-入账, EXPENSE-出账）
     * @param txType           交易业务类型
     * @param remark           备注
     * @param orderId          业务订单号
     * @param txId             链上哈希
     * @param res              链上响应结果
     * @param num              变动数量（基本整型参数）
     * @param createUserBillVO 扩展参数对象（主要用于传递字符串格式的高精度碎片数量）
     */
    @Override
    public void createBillAndUpdateBalance(
            Long userId,
            BigDecimal amount,
            BillType billType,
            FundType fundType,
            TransactionType txType,
            String remark,
            String orderId,
            String txId,
            String res,
            int num,
            CreateUserBillVO createUserBillVO
    ) {
        // 生成或校验订单号
        if (StringUtils.isBlank(orderId)) {
            orderId = "BILL_" + IdWorker.getIdStr();
        }

        // 锁行并获取用户信息，确保余额更新的原子性
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 初始化账单记录对象
        UserBill bill = new UserBill();
        bill.setUserId(userId);
        bill.setUserWalletAddress(user.getUserWalletAddress());
        bill.setTransactionOrderId(orderId);
        bill.setTxId(txId);
        bill.setBillType(billType);
        bill.setFundType(fundType);
        bill.setTransactionType(txType);
        bill.setNum(num);
        bill.setRemark(remark);
        bill.setTransactionTime(LocalDateTime.now());
        bill.setStatus(TransactionStatus.SUCCESS);
        bill.setChainResponse(res);

        // 定义快照变量（用于记录账单变动前后的快照）
        BigDecimal balanceBefore = BigDecimal.ZERO;
        BigDecimal balanceAfter = BigDecimal.ZERO;

        // 根据账单类型分发逻辑
        switch (billType) {
            case FRAGMENT:
                // --- 碎片逻辑处理 ---
                // 确定变动数量：优先取 VO 中的字符串（支持高精度），若为空则取 int 类型的 num
                String changeStr = (createUserBillVO != null && StringUtils.isNotBlank(createUserBillVO.getNum()))
                        ? createUserBillVO.getNum() : String.valueOf(num);
                BigDecimal fragChangeAmount = new BigDecimal(changeStr);
                // 获取当前碎片余额快照（String -> BigDecimal）
                String currentFragStr = user.getFragmentBalance();
                balanceBefore = StringUtils.isNotBlank(currentFragStr) ? new BigDecimal(currentFragStr) : BigDecimal.ZERO;
                // 计算变动后余额
                if (FundType.EXPENSE.equals(fundType)) {
                    balanceAfter = balanceBefore.subtract(fragChangeAmount);
                } else {
                    balanceAfter = balanceBefore.add(fragChangeAmount);
                }
                // 校验余额是否合法
                if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("碎片余额不足");
                }

                // 更新用户表碎片字段（BigDecimal -> String，使用 toPlainString 避免科学计数法）
                String balanceAfterStr = balanceAfter.toPlainString();
                userMapper.update(null, new LambdaUpdateWrapper<User>()
                        .eq(User::getId, userId)
                        .set(User::getFragmentBalance, balanceAfterStr));
                // 设置账单特有字段
                bill.setAmount(BigDecimal.ZERO);      // 碎片账单金额记录为0
                bill.setFragmentNum(balanceAfterStr); // 记录碎片变动后的数量快照
                break;

            case PLATFORM:
                // --- 平台余额逻辑处理 ---
                balanceBefore = (user.getBalance() == null) ? BigDecimal.ZERO : user.getBalance();
                // 计算变动后余额
                if (FundType.EXPENSE.equals(fundType)) {
                    balanceAfter = balanceBefore.subtract(amount);
                } else {
                    balanceAfter = balanceBefore.add(amount);
                }
                // 校验余额是否合法
                if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BusinessException("账户余额不足");
                }
                // 只有当金额不为0时才执行数据库更新，减少无效IO
                if (amount.compareTo(BigDecimal.ZERO) != 0) {
                    userMapper.update(null, new LambdaUpdateWrapper<User>()
                            .eq(User::getId, userId)
                            .set(User::getBalance, balanceAfter));
                }
                bill.setAmount(amount);
                break;

            case ON_CHAIN:
            case ERROR_ORDER:
                // --- 链上流水或异常订单逻辑 ---
                // 此类账单通常只做流水记录，不直接操作本地缓存余额
                balanceBefore = (user.getBalance() == null) ? BigDecimal.ZERO : user.getBalance();
                balanceAfter = balanceBefore; // 余额无变动
                bill.setAmount(amount);
                break;
            default:
                throw new BusinessException("未知的账单类型: " + billType);
        }

        // 统一设置通用快照字段并持久化账单
        // 即使是碎片类型，balanceBefore/After 也会存储数值，方便管理后台通用列表展示
        bill.setBalanceBefore(balanceBefore);
        bill.setBalanceAfter(balanceAfter);
        this.save(bill);
    }

    @Override
    public IPage<UserBill> getAdminBillPage(AdminBillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getUserWalletAddress())) wrapper.like(UserBill::getUserWalletAddress, query.getUserWalletAddress());
        if (query.getBillType() != null) wrapper.eq(UserBill::getBillType, query.getBillType());
        if (query.getFundType() != null) wrapper.eq(UserBill::getFundType, query.getFundType());
        if (query.getTransactionType() != null) wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        if (query.getStatus() != null) wrapper.eq(UserBill::getStatus, query.getStatus());
        if (StringUtils.isNotBlank(query.getStartDate())) wrapper.ge(UserBill::getTransactionTime, DateUtil.parse(query.getStartDate()).toLocalDateTime().with(LocalTime.MIN));
        if (StringUtils.isNotBlank(query.getEndDate())) wrapper.le(UserBill::getTransactionTime, DateUtil.parse(query.getEndDate()).toLocalDateTime().with(LocalTime.MAX));
        wrapper.orderByDesc(UserBill::getTransactionTime).orderByDesc(UserBill::getId);
        return this.page(new Page<>(query.getPage(), query.getSize()), wrapper);
    }

    @Override
    public AdminBillStatisticsVO getPlatformStatistics() {
        AdminBillStatisticsVO vo = new AdminBillStatisticsVO();
        QueryWrapper<User> uq = new QueryWrapper<>();
        uq.select("SUM(balance) as sb");
        List<Map<String, Object>> maps = userMapper.selectMaps(uq);
        if (!maps.isEmpty() && maps.get(0) != null && maps.get(0).get("sb") != null) {
            vo.setTotalUserBalance(new BigDecimal(maps.get(0).get("sb").toString()));
        }

        QueryWrapper<UserBill> dq = new QueryWrapper<>();
        dq.select("SUM(amount) as sa").eq("bill_type", BillType.PLATFORM.getCode()).eq("transaction_type", TransactionType.DEPOSIT.getCode()).eq("status", TransactionStatus.SUCCESS.getCode());
        List<Map<String, Object>> dms = this.baseMapper.selectMaps(dq);
        if (!dms.isEmpty() && dms.get(0) != null && dms.get(0).get("sa") != null) {
            vo.setTotalPlatformDeposit(new BigDecimal(dms.get(0).get("sa").toString()));
        }

        List<UserBill> bills = this.list(new LambdaQueryWrapper<UserBill>().select(UserBill::getAmount, UserBill::getRemark).eq(UserBill::getBillType, BillType.PLATFORM).eq(UserBill::getTransactionType, TransactionType.PURCHASE).eq(UserBill::getStatus, TransactionStatus.SUCCESS));
        BigDecimal totalAmt = BigDecimal.ZERO;
        int totalQty = 0;
        Pattern p = Pattern.compile("x(\\d+)$");
        for (UserBill b : bills) {
            if (b.getAmount() != null) totalAmt = totalAmt.add(b.getAmount());
            if (StringUtils.isNotBlank(b.getRemark())) {
                Matcher m = p.matcher(b.getRemark().trim());
                if (m.find()) totalQty += Integer.parseInt(m.group(1));
            }
        }
        vo.setTotalNftPurchaseAmount(totalAmt);
        vo.setTotalNftSalesCount(totalQty);
        return vo;
    }

    @Override
    public PaymentUsdtMetaVO getPaymentUsdtMeta() throws Exception {
        PaymentUsdtMetaVO vo = new PaymentUsdtMetaVO();
        vo.setContractAddress(paymentUsdtContract.getAddress());
        vo.setUsdtAddress(paymentUsdtContract.usdtAddress());
        vo.setAdminAddress(paymentUsdtContract.adminAddress());
        vo.setExecutorAddress(paymentUsdtContract.executorAddress());
        vo.setTreasuryAddress(paymentUsdtContract.treasuryAddress());
        vo.setMinAmount(new BigDecimal(paymentUsdtContract.minAmount()).divide(new BigDecimal("1000000000000000000"), 6, RoundingMode.HALF_UP));
        return vo;
    }

    @Override
    public IPage<UserBill> getUserBillPage(Long userId, BillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<UserBill>().eq(UserBill::getUserId, userId);
        if (query.getBillType() != null) wrapper.eq(UserBill::getBillType, query.getBillType());
        if (query.getTransactionType() != null) wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        if (query.getFundType() != null) wrapper.eq(UserBill::getFundType, query.getFundType());
        if (query.getTransactionStatus() != null) wrapper.eq(UserBill::getStatus, query.getTransactionStatus());
        if (StringUtils.isNotBlank(query.getStartDate())) wrapper.ge(UserBill::getTransactionTime, DateUtil.parse(query.getStartDate()).toLocalDateTime().with(LocalTime.MIN));
        if (StringUtils.isNotBlank(query.getEndDate())) wrapper.le(UserBill::getTransactionTime, DateUtil.parse(query.getEndDate()).toLocalDateTime().with(LocalTime.MAX));
        wrapper.orderByDesc(UserBill::getTransactionTime).orderByDesc(UserBill::getId);
        return this.page(new Page<>(query.getPage() == null ? 1 : query.getPage(), query.getSize() == null ? 10 : query.getSize()), wrapper);
    }
}