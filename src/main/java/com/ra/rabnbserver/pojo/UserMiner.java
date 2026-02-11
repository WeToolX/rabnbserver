package com.ra.rabnbserver.pojo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ra.rabnbserver.annotation.ColumnComment;
import com.ra.rabnbserver.annotation.ColumnType;
import com.ra.rabnbserver.annotation.DefaultValue;
import com.ra.rabnbserver.exception.Abnormal.model.AbnormalBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("user_miner")
public class UserMiner extends AbnormalBaseEntity {
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
     * 矿机id
     */
    @TableField("miner_id")
    @ColumnComment("矿机id")
    private String minerId;

    /**
     * 矿机类型 0:小型, 1:中型, 2:大型, 3:特殊
     */
    @TableField("miner_type")
    @ColumnComment("矿机类型0,1,2,3")
    private String minerType;

    /**
     * 卡牌NFT销毁状态 (0: 未销毁, 1: 已销毁成功)
     * 对应重试框架检测的目标状态
     */
    @TableField("nft_burn_status")
    @ColumnComment("卡牌NFT销毁状态 0:未销毁, 1:已销毁")
    @DefaultValue("0")
    private Integer nftBurnStatus;

    /**
     * 购买矿机时使用的卡牌ID
     */
    @TableField("nft_card_id")
    @ColumnComment("购买矿机使用的卡牌ID（1-铜/2-银/3-金）")
    @ColumnType("INT")
    private Integer nftCardId;

    /**
     * 卡牌销毁订单号（用于链上 burnWithOrder）
     * 合约升级后必须带订单号，便于链上对账与重试补偿
     * 说明：调用合约时会对该订单号做 keccak256 转 bytes32
     */
    @TableField("nft_burn_order_id")
    @ColumnComment("卡牌销毁订单号（用于链上burnWithOrder）")
    @ColumnType("VARCHAR(100)")
    private String nftBurnOrderId;

    /**
     * 是否已交电费 (0: 否, 1: 是)
     */
    @TableField("is_electricity_paid")
    @ColumnComment("是否已交电费 0:否, 1:是")
    @DefaultValue("0")
    private Integer isElectricityPaid;

    /**
     * 最近一次交费日期 (对应30天有效期起点)
     */
    @TableField("payment_date")
    @ColumnComment("交费日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime paymentDate;

    /**
     * 矿机生命周期状态 (0: 待激活/初始化, 1: 运行中, 2: 已过期/停止)
     */
    @TableField("status")
    @ColumnComment("矿机状态 0:待激活, 1:运行中, 2:已过期")
    @DefaultValue("0")
    private Integer status;

    /**
     * 收益起算日期 (15或3天天等待期结束时间)
     */
    @TableField("eligible_date")
    @ColumnComment("收益起算日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eligibleDate;

    /**
     * 是否已购买加速包 (0: 否, 1: 是)
     */
    @TableField("is_accelerated")
    @ColumnComment("是否已购买加速包 0:否, 1:是")
    @DefaultValue("0")
    private Integer isAccelerated;

    /**
     * 上次收益产生时间
     */
    @TableField("last_reward_time")
    @ColumnComment("上次收益产生时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastRewardTime;
}
