package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.Index;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import com.ra.rabnbserver.enums.IndexType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("gold_quant_account")
@TableComment("黄金量化用户托管费信息")
@Index(name = "uk_gold_quant_account_user", columns = {"user_id"}, type = IndexType.UNIQUE, comment = "用户唯一索引")
public class GoldQuantAccount extends BaseEntity {
    @TableField("user_id")
    @ColumnComment("用户ID")
    private Long userId;

    @TableField("wallet_address")
    @ColumnComment("用户钱包地址")
    @ColumnType("VARCHAR(255)")
    private String walletAddress;

    @TableField("hosting_expire_time")
    @ColumnComment("托管费到期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime hostingExpireTime;
}
