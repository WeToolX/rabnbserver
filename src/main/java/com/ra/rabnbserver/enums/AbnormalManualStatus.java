package com.ra.rabnbserver.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

/**
 * 人工处理状态枚举（err_submit_manual_status）
 */
@Getter
@AllArgsConstructor
public enum AbnormalManualStatus {
    /**
     * 已提交
     */
    SUBMITTED(2000, "已提交"),
    /**
     * 提交异常
     */
    SUBMIT_FAILED(4000, "提交异常"),
    /**
     * 人工处理成功
     */
    MANUAL_SUCCESS(4002, "人工处理成功");

    @EnumValue
    @JsonValue
    private final int code;
    private final String desc;

    @JsonCreator
    public static AbnormalManualStatus fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        for (AbnormalManualStatus status : AbnormalManualStatus.values()) {
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
        for (AbnormalManualStatus status : AbnormalManualStatus.values()) {
            if (status.code == code) {
                return status.desc;
            }
        }
        return null;
    }
}
