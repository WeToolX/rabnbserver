package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
@AllArgsConstructor
public enum GoldQuantCommissionType implements BaseEnum {
    REWARD("REWARD", "奖励分成"),
    DISTRIBUTION("DISTRIBUTION", "分销分成");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;

    @JsonCreator
    public static GoldQuantCommissionType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (GoldQuantCommissionType type : values()) {
            if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
}
