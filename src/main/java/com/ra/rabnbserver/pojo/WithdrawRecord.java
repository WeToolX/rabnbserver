package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import com.ra.rabnbserver.enums.WithdrawStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@TableComment("用户提现申请记录表")
@TableName("withdraw_record")
public class WithdrawRecord extends BaseEntity {

    /**
     * 用户ID
     */
    @TableField("user_id")
    @ColumnComment("用户ID")
    private Long userId;

    /**
     * 提现钱包地址
     */
    @TableField("user_wallet_address")
    @ColumnComment("提现钱包地址")
    private String userWalletAddress;

    /**
     * 申请金额(没有扣除手续费的)
     */
    @TableField("post_amount")
    @ColumnComment("申请金额(没有扣除手续费的)")
    @ColumnType("DECIMAL(65, 18)")
    private BigDecimal postAmount;

    /**
     * 提现总金额(扣减的余额)
     */
    @TableField("amount")
    @ColumnComment("提现总金额(扣减的余额)")
    @ColumnType("DECIMAL(65, 18)")
    private BigDecimal amount;

    /**
     * 实际到账金额
     */
    @TableField("actual_amount")
    @ColumnComment("实际到账金额")
    @ColumnType("DECIMAL(65, 18)")
    private BigDecimal actualAmount;

    /**
     * 扣除的手续费
     */
    @TableField("fee_amount")
    @ColumnComment("扣除的手续费")
    @ColumnType("DECIMAL(65, 18)")
    private BigDecimal feeAmount;

    /**
     * 手续费率(小数)
     */
    @TableField("fee_rate")
    @ColumnComment("手续费率(小数)")
    @ColumnType("DECIMAL(18, 5)")
    private BigDecimal feeRate;

    /**
     * 状态: 0-待审核, 1-已通过, 2-已驳回
     */
    @TableField("status")
    @ColumnComment("状态: 0-待审核, 1-已通过, 2-已驳回")
    private WithdrawStatus status;

    /**
     * 关联的账单订单号
     */
    @TableField("order_id")
    @ColumnComment("关联的账单订单号")
    private String orderId;

    /**
     * 审核备注/驳回原因
     */
    @TableField("remark")
    @ColumnComment("审核备注/驳回原因")
    private String remark;
}