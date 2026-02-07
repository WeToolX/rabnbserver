package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 账单类型
 */
@Getter
@AllArgsConstructor
public enum BillType  implements BaseEnum{
    PLATFORM("PLATFORM", "平台资金流水"),
    ON_CHAIN("ON_CHAIN", "链上资金流水"),
    FRAGMENT("FRAGMENT", "碎片流水"),
    ERROR_ORDER("ERROR_ORDER","异常账单");

    @EnumValue // 标记数据库存储的值
    @JsonValue  // 标记前端收到的值
    private final String code;
    private final String desc;

    @JsonCreator
    public static BillType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null; // 关键：解决空字符串 "" 报错问题
        }
        for (BillType type : BillType.values()) {
            if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据code获取中文描述
     */
    public static String getDescByCode(String code) {
        for (BillType type : BillType.values()) {
            if (type.code.equals(code)) {
                return type.desc;
            }
        }
        return null;
    }
}