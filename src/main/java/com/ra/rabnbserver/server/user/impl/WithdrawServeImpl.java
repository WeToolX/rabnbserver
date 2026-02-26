package com.ra.rabnbserver.server.user.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ra.rabnbserver.VO.WithdrawSettingsVO;
import com.ra.rabnbserver.dto.user.WithdrawAuditDTO;
import com.ra.rabnbserver.dto.withdraw.AdminWithdrawQueryDTO;
import com.ra.rabnbserver.dto.withdraw.UserWithdrawQueryDTO;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import com.ra.rabnbserver.enums.WithdrawStatus;
import com.ra.rabnbserver.exception.BusinessException;
import com.ra.rabnbserver.mapper.WithdrawRecordMapper;
import com.ra.rabnbserver.pojo.User;
import com.ra.rabnbserver.pojo.WithdrawRecord;
import com.ra.rabnbserver.server.sys.SystemConfigServe;
import com.ra.rabnbserver.server.user.UserBillServe;
import com.ra.rabnbserver.server.user.UserServe;
import com.ra.rabnbserver.server.user.WithdrawServe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawServeImpl extends ServiceImpl<WithdrawRecordMapper, WithdrawRecord> implements WithdrawServe {
    private final UserServe userServe;
    private final UserBillServe userBillServe;
    private final SystemConfigServe systemConfigServe; // 引入配置服务读取手续费


    @Transactional(rollbackFor = Exception.class)
    @Override
    public void applyWithdraw(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("提现金额必须大于0");
        }

        // 1. 获取系统配置中的提现参数
        WithdrawSettingsVO settings = systemConfigServe.getConfigObject("WITHDRAW_SETTINGS", WithdrawSettingsVO.class);
        if (settings == null) {
            throw new BusinessException("系统提现参数未配置，请联系管理员");
        }

        // 2. 校验最低提现金额
        if (settings.getMinAmount() != null && amount.compareTo(settings.getMinAmount()) < 0) {
            throw new BusinessException("单笔最低提现金额为: " + settings.getMinAmount());
        }

        User user = userServe.getById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        // 3. 计算手续费和实际到账金额
        BigDecimal feeRate = settings.getFeeRate() != null ? settings.getFeeRate() : BigDecimal.ZERO;
        // 计算手续费金额，默认保留 18 位小数，向上/四舍五入
        BigDecimal feeAmount = amount.multiply(feeRate).setScale(18, RoundingMode.HALF_UP);
        BigDecimal actualAmount = amount.subtract(feeAmount);

        // 如果提现金额太小，导致扣完手续费变成负数或者0
        if (actualAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("提现金额扣除手续费后必须大于0");
        }

        // 4. 生成唯一订单号
        String orderId = "WD_" + IdWorker.getIdStr();

        // 5. 扣除用户平台余额 (这里扣除的是申请的总金额 amount)
        userBillServe.createBillAndUpdateBalance(
                userId,
                amount,
                BillType.PLATFORM,
                FundType.EXPENSE,            // 出账
                TransactionType.WITHDRAWAL,  // 提现类型
                "用户申请提现扣款(含手续费)",
                orderId,
                null,
                null,
                0,
                null
        );

        // 6. 创建提现申请记录
        WithdrawRecord record = new WithdrawRecord();
        record.setUserId(userId);
        record.setUserWalletAddress(user.getUserWalletAddress());
        record.setAmount(amount);              // 总金额
        record.setActualAmount(actualAmount);  // 实际到账
        record.setFeeAmount(feeAmount);        // 手续费
        record.setFeeRate(feeRate);            // 费率
        record.setStatus(WithdrawStatus.PENDING);
        record.setOrderId(orderId);
        this.save(record);

        log.info("用户 {} 提交提现申请，总金额: {}, 手续费: {}, 实际到账: {}, 记录ID: {}",
                userId, amount, feeAmount, actualAmount, record.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void auditWithdraw(WithdrawAuditDTO dto) {
        WithdrawRecord record = this.getById(dto.getRecordId());
        if (record == null) {
            throw new BusinessException("提现记录不存在");
        }
        if (!WithdrawStatus.PENDING.equals(record.getStatus())) {
            throw new BusinessException("该提现记录已被处理，请勿重复操作");
        }

        if (Boolean.TRUE.equals(dto.getIsPass())) {
            // ================= 审核通过 =================
            boolean updated = this.update(new LambdaUpdateWrapper<WithdrawRecord>()
                    .eq(WithdrawRecord::getId, record.getId())
                    .eq(WithdrawRecord::getStatus, WithdrawStatus.PENDING)
                    .set(WithdrawRecord::getStatus, WithdrawStatus.APPROVED)
                    .set(WithdrawRecord::getRemark, dto.getRemark() == null ? "审核通过" : dto.getRemark())
            );
            if (!updated) {
                throw new BusinessException("并发操作，审核失败");
            }
            // 注意：审核通过后实际转账时，应当按 record.getActualAmount() (实际到账金额) 进行链上转账！！！
            log.info("提现单 {} 审核通过，应为用户转账实际金额: {}", record.getId(), record.getActualAmount());

        } else {
            // ================= 审核驳回 =================
            boolean updated = this.update(new LambdaUpdateWrapper<WithdrawRecord>()
                    .eq(WithdrawRecord::getId, record.getId())
                    .eq(WithdrawRecord::getStatus, WithdrawStatus.PENDING)
                    .set(WithdrawRecord::getStatus, WithdrawStatus.REJECTED)
                    .set(WithdrawRecord::getRemark, dto.getRemark() == null ? "审核驳回" : dto.getRemark())
            );
            if (!updated) {
                throw new BusinessException("并发操作，审核失败");
            }

            // 退还用户扣除的总金额 (申请时的 amount 包含手续费，必须全额退还)
            String refundOrderId = "REFUND_" + record.getOrderId();
            userBillServe.createBillAndUpdateBalance(
                    record.getUserId(),
                    record.getAmount(), // 这里使用的是原 amount（总金额）
                    BillType.PLATFORM,
                    FundType.INCOME,
                    TransactionType.WITHDRAWAL,
                    "提现审核驳回退款。原单号: " + record.getOrderId(),
                    refundOrderId,
                    null,
                    null,
                    0,
                    null
            );
            log.info("提现单 {} 审核被驳回，原资金 {} 已退回用户账户", record.getId(), record.getAmount());
        }
    }

    @Override
    public IPage<WithdrawRecord> getUserWithdrawPage(Long userId, UserWithdrawQueryDTO query) {
        if (query == null) query = new UserWithdrawQueryDTO();
        Page<WithdrawRecord> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<>();

        // 只能查询自己的记录
        wrapper.eq(WithdrawRecord::getUserId, userId);

        // 状态筛选
        if (query.getStatus() != null) {
            wrapper.eq(WithdrawRecord::getStatus, query.getStatus());
        }
        // 订单号模糊匹配
        if (StrUtil.isNotBlank(query.getOrderId())) {
            wrapper.like(WithdrawRecord::getOrderId, query.getOrderId());
        }
        // 时间范围筛选
        if (StrUtil.isNotBlank(query.getStartDate())) {
            wrapper.ge(WithdrawRecord::getCreateTime, DateUtil.parse(query.getStartDate()).toLocalDateTime().with(LocalTime.MIN));
        }
        if (StrUtil.isNotBlank(query.getEndDate())) {
            wrapper.le(WithdrawRecord::getCreateTime, DateUtil.parse(query.getEndDate()).toLocalDateTime().with(LocalTime.MAX));
        }

        // 按创建时间倒序
        wrapper.orderByDesc(WithdrawRecord::getCreateTime);

        return this.page(page, wrapper);
    }

    @Override
    public IPage<WithdrawRecord> getAdminWithdrawPage(AdminWithdrawQueryDTO query) {
        if (query == null) query = new AdminWithdrawQueryDTO();
        Page<WithdrawRecord> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<WithdrawRecord> wrapper = new LambdaQueryWrapper<>();

        // 用户ID精准匹配
        if (query.getUserId() != null) {
            wrapper.eq(WithdrawRecord::getUserId, query.getUserId());
        }
        // 钱包地址模糊匹配
        if (StrUtil.isNotBlank(query.getUserWalletAddress())) {
            wrapper.like(WithdrawRecord::getUserWalletAddress, query.getUserWalletAddress());
        }
        // 状态筛选
        if (query.getStatus() != null) {
            wrapper.eq(WithdrawRecord::getStatus, query.getStatus());
        }
        // 订单号模糊匹配
        if (StrUtil.isNotBlank(query.getOrderId())) {
            wrapper.like(WithdrawRecord::getOrderId, query.getOrderId());
        }
        // 时间范围筛选
        if (StrUtil.isNotBlank(query.getStartDate())) {
            wrapper.ge(WithdrawRecord::getCreateTime, DateUtil.parse(query.getStartDate()).toLocalDateTime().with(LocalTime.MIN));
        }
        if (StrUtil.isNotBlank(query.getEndDate())) {
            wrapper.le(WithdrawRecord::getCreateTime, DateUtil.parse(query.getEndDate()).toLocalDateTime().with(LocalTime.MAX));
        }

        // 按创建时间倒序
        wrapper.orderByDesc(WithdrawRecord::getCreateTime);

        return this.page(page, wrapper);
    }
}
