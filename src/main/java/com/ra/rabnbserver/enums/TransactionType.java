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
public enum TransactionType implements BaseEnum{
    PURCHASE("PURCHASE", "购买"),
    SELL("SELL", "卖出"),
    DEPOSIT("DEPOSIT", "充值"),
    WITHDRAWAL("WITHDRAWAL", "提现"),
    EXCHANGE("EXCHANGE", "闪兑"),
    REWARD("REWARD", "奖励"),
    PROFIT("PROFIT", "收益"),
    TRANSFER("TRANSFER", "转账");

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