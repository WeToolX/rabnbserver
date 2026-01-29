package com.ra.rabnbserver.server.user.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.dto.BillQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.UserBillMapper;
import com.ra.rabnbserver.mapper.UserMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.UserBill;
import com.ra.rabnbserver.server.user.userBillServe;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
@RequiredArgsConstructor
public class userBillServeImpl extends ServiceImpl<UserBillMapper, UserBill> implements userBillServe {

    private final UserMapper userMapper;


    /**
     * 统一创建账单并同步更新用户展示余额
     * @param userId  用户id
     * @param amount 操作金额
     * @param billType  账本类型
     * @param fundType  资金类型
     * @param txType 交易类型
     * @param remark 备注
     * @param orderId 订单id
     * @param txId   交易哈希
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBillAndUpdateBalance(Long userId, BigDecimal amount, BillType billType,
                                           FundType fundType, TransactionType txType,
                                           String remark, String orderId, String txId) {


        // 1. 默认值处理
        // 如果 orderId 为空，生成一个带前缀的唯一订单号（例如：BILL_17823...）
        if (StringUtils.isBlank(orderId)) {
            orderId = "BILL_" + IdWorker.getIdStr();
        }
        // 如果 remark 为空，默认使用交易类型的描述
        if (StringUtils.isBlank(remark)) {
            remark = txType.getDesc();
        }

        // 1. 悲观锁锁定用户，确保流水计算的串行化
        // 即便不更新用户余额，锁定用户也是为了防止该用户的多条流水（同类型）并发插入导致余额计算错乱
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 2. 动态获取该用户【当前账单类型】的上一笔最后余额
        // 逻辑：如果是平台账单查平台余额，如果是链上账单查链上余额
        BigDecimal balanceBefore = BigDecimal.ZERO;
        UserBill lastBill = this.getOne(new LambdaQueryWrapper<UserBill>()
                .eq(UserBill::getUserId, userId)
                .eq(UserBill::getBillType, billType) // 这里改为动态匹配传入的 billType
                .orderByDesc(UserBill::getId)
                .last("LIMIT 1"));

        if (lastBill != null) {
            balanceBefore = lastBill.getBalanceAfter();
        }

        // 3. 计算金额变动
        BigDecimal changeAmount = amount;
        if (FundType.EXPENSE.equals(fundType)) {
            changeAmount = amount.negate();
        }

        // 4. 计算变动后的余额
        BigDecimal balanceAfter = balanceBefore.add(changeAmount);

        // 5. 余额合法性校验（通常平台账单和链上账单都不允许余额小于0）
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(billType.getDesc() + "账户余额不足");
        }

        // 6. 插入新账单
        UserBill newBill = new UserBill();
        newBill.setUserId(userId);
        newBill.setUserWalletAddress(user.getUserWalletAddress());
        newBill.setTransactionOrderId(orderId);
        newBill.setTxId(txId);
        newBill.setBillType(billType);
        newBill.setFundType(fundType);
        newBill.setTransactionType(txType);
        newBill.setAmount(amount);
        newBill.setBalanceBefore(balanceBefore);
        newBill.setBalanceAfter(balanceAfter);
        newBill.setRemark(remark);
        newBill.setTransactionTime(LocalDateTime.now());
        this.save(newBill);

        // 7. 同步更新用户表的余额字段（仅针对 PLATFORM 类型）
        if (BillType.PLATFORM.equals(billType)) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getBalance, balanceAfter));
        }

        log.info("账单记录成功: 类型={}, 用户={}, 变动前={}, 变动后={}",
                billType, userId, balanceBefore, balanceAfter);
    }

    @Override
    public IPage<UserBill> getUserBillPage(Long userId, BillQueryDTO query) {
        LambdaQueryWrapper<UserBill> wrapper = new LambdaQueryWrapper<>();

        // 1. 锁定当前用户
        wrapper.eq(UserBill::getUserId, userId);

        // 2. 账单类型和交易类型筛选
        if (query.getBillType() != null) {
            wrapper.eq(UserBill::getBillType, query.getBillType());
        }
        if (query.getTransactionType() != null) {
            wrapper.eq(UserBill::getTransactionType, query.getTransactionType());
        }
        if (query.getFundType() != null) {
            wrapper.eq(UserBill::getFundType, query.getFundType());
        }

        // 3. 日期字符串转换与筛选
        // 假设传入格式为 "yyyy-MM-dd"
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (StringUtils.isNotBlank(query.getStartDate())) {
            // 转换为当天的 00:00:00
            LocalDateTime start = LocalDate.parse(query.getStartDate(), formatter).atStartOfDay();
            wrapper.ge(UserBill::getTransactionTime, start);
        }

        if (StringUtils.isNotBlank(query.getEndDate())) {
            // 转换为当天的 23:59:59
            LocalDateTime end = LocalDate.parse(query.getEndDate(), formatter).atTime(LocalTime.MAX);
            wrapper.le(UserBill::getTransactionTime, end);
        }

        // 4. 按时间倒序排序
        wrapper.orderByDesc(UserBill::getId);

        // 5. 执行分页查询
        return this.page(new Page<>(query.getPage(), query.getSize()), wrapper);
    }
}