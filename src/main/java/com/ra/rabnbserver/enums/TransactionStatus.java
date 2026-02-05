package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 交易状态枚举
 */
@Getter
@AllArgsConstructor
public enum TransactionStatus implements BaseEnum {
    /**
     * 处理中
     */
    PENDING("0", "处理中"),
    /**
     * 成功
     */
    SUCCESS("1", "成功"),
    /**
     * 失败
     */
    FAILED("2", "失败");

    @EnumValue
    @JsonValue
    private final String code;
    private final String desc;


    @JsonCreator
    public static TransactionStatus fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (TransactionStatus status : TransactionStatus.values()) {
            // 同时匹配 "1" 或者 "SUCCESS"
            if (status.code.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }

    public static String getDescByCode(String code) {
        for (TransactionStatus status : TransactionStatus.values()) {
            if (status.code.equals(code)) {
                return status.desc;
            }
        }
        return null;
    }
}
