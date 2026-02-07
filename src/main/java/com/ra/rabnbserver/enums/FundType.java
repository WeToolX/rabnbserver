package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 资金类型
 */
@Getter
@AllArgsConstructor
public enum FundType  implements BaseEnum{
    INCOME("INCOME", "入账"),
    EXPENSE("EXPENSE", "出账");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;


    @JsonCreator
    public static FundType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (FundType type : FundType.values()) {
            if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }

    public static String getDescByCode(String code) {
        for (FundType type : FundType.values()) {
            if (type.code.equals(code)) {
                return type.desc;
            }
        }
        return null;
    }
}