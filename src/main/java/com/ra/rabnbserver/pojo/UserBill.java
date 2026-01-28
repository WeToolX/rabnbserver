package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import com.ra.rabnbserver.enums.BillType;
import com.ra.rabnbserver.enums.FundType;
import com.ra.rabnbserver.enums.TransactionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户账单 - 多用途资金流水表
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableComment("用户账单表")
@TableName("user_bill")
public class UserBill extends BaseEntity {


    @ColumnComment("用户ID")
    @TableField("user_id")
    private Long userId;

    @ColumnComment("用户钱包地址")
    @TableField("user_wallet_address")
    @ColumnType("VARCHAR(255)")
    private String userWalletAddress;

    @ColumnComment("系统交易单号")
    @TableField("transaction_order_id")
    @ColumnType("VARCHAR(100)")
    private String transactionOrderId;

    @ColumnComment("区块链交易哈希(TxHash)")
    @TableField("tx_id")
    @ColumnType("VARCHAR(100)")
    private String txId;

    /**
     * 账单类型：PLATFORM-平台资金, ON_CHAIN-链上资金
     */
    @ColumnComment("账单类型（PLATFORM-平台, ON_CHAIN-链上）")
    @TableField("bill_type")
    @ColumnType("VARCHAR(20)")
    private BillType billType;

    /**
     * 资金类型：INCOME-入账, EXPENSE-出账
     */
    @ColumnComment("资金类型（INCOME-入账, EXPENSE-出账）")
    @TableField("fund_type")
    @ColumnType("VARCHAR(20)")
    private FundType fundType;

    /**
     * 交易业务类型：PURCHASE, SELL, REWARD等
     */
    @ColumnComment("交易类型（PURCHASE-购买, SELL-卖出, DEPOSIT-充值, WITHDRAWAL-提现, EXCHANGE-闪兑, REWARD-奖励, PROFIT-收益）")
    @TableField("transaction_type")
    @ColumnType("VARCHAR(50)")
    private TransactionType transactionType;

    @ColumnComment("交易金额")
    @TableField("amount")
    @ColumnType("VARCHAR(64)")
    private String amount;

    @ColumnComment("交易前余额")
    @TableField("balance_before")
    @ColumnType("VARCHAR(64)")
    private String balanceBefore;

    @ColumnComment("交易后余额")
    @TableField("balance_after")
    @ColumnType("VARCHAR(64)")
    private String balanceAfter;

    @ColumnComment("交易备注")
    @TableField("remark")
    @ColumnType("TEXT")
    private String remark;

    @ColumnComment("交易时间")
    @TableField("transaction_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transactionTime;
}