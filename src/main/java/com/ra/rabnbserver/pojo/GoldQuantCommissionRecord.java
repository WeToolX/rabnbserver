package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.Index;
import com.ra.rabnbserver.annotation.Indexes;
import com.ra.rabnbserver.annotation.TableComment;
import com.ra.rabnbserver.common.BaseEntity;
import com.ra.rabnbserver.enums.GoldQuantCommissionType;
import com.ra.rabnbserver.enums.IndexType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("gold_quant_commission_record")
@TableComment("黄金量化分成记录")
@Indexes({
        @Index(name = "idx_gq_commission_beneficiary", columns = {"beneficiary_user_id"}, type = IndexType.NORMAL, comment = "收益用户索引"),
        @Index(name = "idx_gq_commission_source", columns = {"source_user_id"}, type = IndexType.NORMAL, comment = "来源用户索引"),
        @Index(name = "idx_gq_commission_order", columns = {"source_order_id"}, type = IndexType.NORMAL, comment = "来源订单索引")
})
public class GoldQuantCommissionRecord extends BaseEntity {
    @TableField("beneficiary_user_id")
    @ColumnComment("收益用户ID")
    private Long beneficiaryUserId;

    @TableField("beneficiary_wallet_address")
    @ColumnComment("收益用户钱包地址")
    @ColumnType("VARCHAR(255)")
    private String beneficiaryWalletAddress;

    @TableField("source_user_id")
    @ColumnComment("来源用户ID")
    private Long sourceUserId;

    @TableField("source_wallet_address")
    @ColumnComment("来源用户钱包地址")
    @ColumnType("VARCHAR(255)")
    private String sourceWalletAddress;

    @TableField("source_order_id")
    @ColumnComment("来源订单号")
    @ColumnType("VARCHAR(100)")
    private String sourceOrderId;

    @TableField("source_bill_id")
    @ColumnComment("来源扣款账单ID")
    private Long sourceBillId;

    @TableField("commission_bill_id")
    @ColumnComment("分成入账账单ID")
    private Long commissionBillId;

    @TableField("commission_type")
    @ColumnComment("分成类型 REWARD/DISTRIBUTION")
    @ColumnType("VARCHAR(30)")
    private GoldQuantCommissionType commissionType;

    @TableField("level")
    @ColumnComment("本次计算等级")
    private Integer level;

    @TableField("generation")
    @ColumnComment("代数")
    private Integer generation;

    @TableField("ratio")
    @ColumnComment("分成比例")
    @ColumnType("DECIMAL(18, 8)")
    private BigDecimal ratio;

    @TableField("order_amount")
    @ColumnComment("订单实付金额")
    @ColumnType("DECIMAL(65, 18)")
    private BigDecimal orderAmount;

    @TableField("commission_amount")
    @ColumnComment("分成金额")
    @ColumnType("DECIMAL(65, 18)")
    private BigDecimal commissionAmount;

    @TableField("remark")
    @ColumnComment("备注")
    @ColumnType("TEXT")
    private String remark;
}
