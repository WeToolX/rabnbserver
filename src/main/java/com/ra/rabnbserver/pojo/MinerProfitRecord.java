package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.exception.Abnormal.model.AbnormalBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    /**
     * 锁仓类型 0:直接分发, 1:L1, 2:L2, 3:L3
     */
    @ColumnComment("锁仓类型 0:直接分发, 1:L1, 2:L2, 3:L3")
    @TableField("lock_type")
    private Integer lockType; // 0:直接分发, 1:L1, 2:L2, 3:L3

    /**
     * 分发类型 1:入仓, 2:直接分发
     */
    @ColumnComment("分发类型 1:入仓, 2:直接分发")
    @TableField("dist_type")
    private Integer distType; // 1:入仓, 2:直接分发

    @TableField("actual_order_id")
    @ColumnComment("实际生成的订单号(传给合约)")
    private Long actualOrderId;

    /**
     * 实际收益发放成功时间
     */
    @TableField("payout_time")
    @ColumnComment("实际收益发放成功时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime payoutTime;
}