package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BillType {
    PLATFORM("PLATFORM", "平台资金流水"),
    ON_CHAIN("ON_CHAIN", "链上资金流水");

    @EnumValue // 标记数据库存储的值
    @JsonValue  // 标记前端收到的值
    private final String code;
    private final String desc;

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