package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 异常主状态枚举（err_status）
 */
@Getter
@AllArgsConstructor
public enum AbnormalStatus {
    /**
     * 正常
     */
    NORMAL(2000, "正常"),
    /**
     * 异常待自动处理
     */
    WAIT_AUTO(4000, "异常待自动处理"),
    /**
     * 异常需人工处理
     */
    WAIT_MANUAL(4001, "异常需人工处理"),
    /**
     * 自动处理成功
     */
    AUTO_SUCCESS(2001, "自动处理成功"),
    /**
     * 人工处理成功
     */
    MANUAL_SUCCESS(2002, "人工处理成功");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;

    @JsonCreator
    public static AbnormalStatus fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (AbnormalStatus status : AbnormalStatus.values()) {
            if (String.valueOf(status.code).equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据 code 获取描述
     *
     * @param code 状态码
     * @return 描述
     */
    public static String getDescByCode(int code) {
        for (AbnormalStatus status : AbnormalStatus.values()) {
            if (status.code == code) {
                return status.desc;
            }
        }
        return null;
    }
}
