package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 交易类型
 */
@Getter
@AllArgsConstructor
public enum TransactionType implements BaseEnum {
    PURCHASE("PURCHASE", "购买"),
    SELL("SELL", "卖出"),
    DEPOSIT("DEPOSIT", "充值"),
    WITHDRAWAL("WITHDRAWAL", "提现"),
    EXCHANGE("EXCHANGE", "闪兑"),
    REWARD("REWARD", "奖励"),
    PROFIT("PROFIT", "收益"),
    TRANSFER("TRANSFER", "转账"),
    MINER_ELECTRICITY("MINER_ELECTRICITY", "矿机电费"),
    MINER_ELECTRICITY_REWARD("MINER_ELECTRICITY_REWARD", "矿机电费分成"),
    GOLD_QUANT("GOLD_QUANT", "黄金量化"),
    GOLD_QUANT_REWARD("GOLD_QUANT_REWARD", "黄金量化奖励分成"),
    ADMIN_MANUALLY_DISTRIBUTES_NFT("ADMIN_MANUALLY_DISTRIBUTES_NFT", "管理员手动发放NFT"),
    GOLD_QUANT_DISTRIBUTION("GOLD_QUANT_DISTRIBUTION", "黄金量化分销分成");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    @JsonCreator
    public static TransactionType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (TransactionType type : TransactionType.values()) {
            if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescByCode(String code) {
        for (TransactionType type : TransactionType.values()) {
            if (type.code.equals(code)) {
                return type.desc;
            }
        }
        return null;
    }
}
