package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FundType {
    INCOME("INCOME", "入账"),
    EXPENSE("EXPENSE", "出账");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    public static String getDescByCode(String code) {
        for (FundType type : FundType.values()) {
            if (type.code.equals(code)) {
                return type.desc;
            }
        }
        return null;
    }
}