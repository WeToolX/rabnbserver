package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * ETF卡牌发行批次表
 * 用于管理卡牌的库存、发行总量及当前激活的批次
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableComment("ETF卡牌发行批次表")
@TableName("etf_card")
public class ETFCard extends BaseEntity {


    /**
     * 批次名称
     */
    @ColumnComment("批次名称")
    @TableField("batch_name")
    @ColumnType("VARCHAR(100)")
    private String batchName;

    /**
     * 批次编号
     */
    @ColumnComment("批次编号")
    @TableField("batch_no")
    @ColumnType("VARCHAR(50)")
    private String batchNo;

    /**
     * 该批次发行总量
     */
    @ColumnComment("该批次发行总量")
    @TableField("total_supply")
    @ColumnType("INT(11)")
    private Integer totalSupply;

    /**
     * 已售/已铸造数量
     */
    @ColumnComment("已售/已铸造数量")
    @TableField("sold_count")
    @ColumnType("INT(11)")
    private Integer soldCount;

    /**
     * 当前库存（剩余可售数量）
     */
    @ColumnComment("当前库存（剩余可售数量）")
    @TableField("inventory")
    @ColumnType("INT(11)")
    private Integer inventory;

    /**
     * 该批次单价(USDT)
     */
    @ColumnComment("该批次单价(USDT)")
    @TableField("unit_price")
    @ColumnType("DECIMAL(36, 18)")
    private BigDecimal unitPrice;

    /**
     * 是否为当前激活批次
     * 0-否，1-是
     * 业务逻辑需保证全表只有一个1
     */
    @ColumnComment("是否为当前激活批次(0-否, 1-是)")
    @TableField("is_current")
    @ColumnType("TINYINT(1)")
    private Integer isCurrent;

    /**
     * 状态(0-停用, 1-启用)
     */
    @ColumnComment("状态(0-停用, 1-启用)")
    @TableField("status")
    @ColumnType("TINYINT(1)")
    private Integer status;

    /**
     * 备注
     */
    @ColumnComment("备注")
    @TableField("remark")
    @ColumnType("VARCHAR(255)")
    private String remark;
}
