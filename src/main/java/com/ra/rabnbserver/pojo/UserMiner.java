package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_miner")
public class UserMiner extends BaseEntity {
    /**
     * 用户id
     */
    @TableField("user_id")
    @ColumnComment("用户id")
    private Long userId;
    /**
     * 用户钱包地址
     */
    @TableField("wallet_address")
    @ColumnComment("用户钱包地址")
    private String walletAddress;
    /**
     * 矿机id// 代码写死，如 "M001"
     */
    @TableField("miner_id")
    @ColumnComment("矿机id")
    private String minerId;

    /**
     * 矿机类型
     */
    @TableField("miner_type")
    @ColumnComment("矿机类型")
    private String minerType;
    /**
     * 是否已交电费// 0:否, 1:是
     */
    @TableField("is_electricity_paid")
    @ColumnComment("是否已交电费0:否, 1:是")
    @DefaultValue("0")
    private Integer isElectricityPaid;
    /**
     * 交费日期
     */
    @TableField("payment_date")
    @ColumnComment("交费日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentDate;
    /**
     * 当前矿机是否已激活// 0:待激活, 1:已激活
     */
    @TableField("status")
    @ColumnComment("当前矿机是否已激活0:待激活, 1:已激活")
    @DefaultValue("0")
    private Integer status;
}