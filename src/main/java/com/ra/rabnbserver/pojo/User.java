package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@TableComment("用户表")
@TableName("user")
public class User extends BaseEntity {

    /**
     *用户名
     */
    @TableField("user_name")
    @ColumnComment("用户名")
    private String userName;


    /**
     * 用户钱包地址
     */
    @TableField("user_wallet_address")
    @ColumnComment("用户钱包地址")
    private String userWalletAddress;


    /**
     * 用户账户余额(作为余额缓存，实际余额需要根据账本数据进行计算)
     */
    @ColumnComment("账户余额(作为余额缓存，实际余额需要根据账本数据进行计算)")
    @TableField("balance")
    @DefaultValue("0.000000000000000000")
    @ColumnType("DECIMAL(36, 18)")
    private BigDecimal balance;

}
