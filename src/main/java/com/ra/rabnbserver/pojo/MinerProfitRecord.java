package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.exception.Abnormal.model.AbnormalBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("miner_profit_record")
public class MinerProfitRecord extends AbnormalBaseEntity {
    /**
     * 用户ID
     */
    @TableField("user_id")
    @ColumnComment("用户ID")
    private Long userId;

    /**
     * 用户钱包地址
     */
    @TableField("wallet_address")
    @ColumnComment("用户钱包地址")
    private String walletAddress;

    /**
     * 收益金额
     */
    @TableField("amount")
    @ColumnComment("收益金额")
    private BigDecimal amount;

    /**
     * 矿机类型
     */
    @TableField("miner_type")
    @ColumnComment("矿机类型")
    private String minerType;

    /**
     * 矿机id
     */
    @TableField("miner_id")
    @ColumnComment("矿机id")
    private String minerId;

    /**
     * 锁仓月份
     */
    @TableField("lock_months")
    @ColumnComment("锁仓月份")
    private Integer lockMonths;

    /**
     * 合约分发状态 (0: 未发放/分发中, 1: 已发放成功)
     * 对应重试框架检测的目标状态
     */
    @TableField("payout_status")
    @ColumnComment("合约分发状态 0:未发放, 1:已发放")
    private Integer payoutStatus;

    /**
     * 链上返回响应（原始JSON数据）
     */
    @ColumnComment("链上返回响应")
    @TableField("chain_response")
    @ColumnType("TEXT")
    private String chainResponse;

    /**
     * 区块链交易哈希(TxHash)
     */
    @ColumnComment("交易哈希")
    @TableField("tx_id")
    @ColumnType("VARCHAR(100)")
    private String txId;

    /**
     * 记录状态 (0: 无效, 1: 有效)
     */
    @TableField("status")
    @ColumnComment("记录状态 0:无效, 1:有效")
    private Integer status;
}