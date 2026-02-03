package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 矿机收益记录表
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("miner_profit_record")
public class MinerProfitRecord extends BaseEntity {
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
     * 锁仓月份
     */
    @TableField("lock_months")
    @ColumnComment("锁仓月份")
    private Integer lockMonths;
    /**
     * 链上返回响应（原始JSON数据）
     */
    @ColumnComment("链上返回响应（原始JSON数据）")
    @TableField("chain_response")
    @ColumnType("TEXT")
    private String chainResponse;


    /**
     * 区块链交易哈希(TxHash)
     */
    @ColumnComment("区块链交易哈希(TxHash)")
    @TableField("tx_id")
    @ColumnType("VARCHAR(100)")
    private String txId;
}