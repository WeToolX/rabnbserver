package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.annotation.Index;
import com.ra.rabnbserver.annotation.Indexes;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import com.ra.rabnbserver.enums.IndexType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("gold_quant_window")
@TableComment("黄金量化窗口")
@Indexes({
        @Index(name = "idx_gold_quant_window_user", columns = {"user_id"}, type = IndexType.NORMAL, comment = "用户索引"),
        @Index(name = "idx_gold_quant_window_expire", columns = {"maintenance_expire_time", "id"}, type = IndexType.NORMAL, comment = "到期时间索引")
})
public class GoldQuantWindow extends BaseEntity {
    @TableField("user_id")
    @ColumnComment("用户ID")
    private Long userId;

    @TableField("wallet_address")
    @ColumnComment("用户钱包地址")
    @ColumnType("VARCHAR(255)")
    private String walletAddress;

    @TableField("window_no")
    @ColumnComment("窗口编号")
    @ColumnType("VARCHAR(64)")
    private String windowNo;

    @TableField("maintenance_expire_time")
    @ColumnComment("窗口维护费到期时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime maintenanceExpireTime;

    @TableField("status")
    @ColumnComment("状态 1正常 0停用")
    @DefaultValue("1")
    private Integer status;
}
