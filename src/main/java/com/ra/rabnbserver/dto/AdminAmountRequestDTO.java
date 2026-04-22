package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class AdminAmountRequestDTO {
    /**
     * 用户id
     */
    private Long userId;
    /**
     * 金额
     */
    private String amount;

    /**
     * 自定义资金来源或备注；例如：v1 团队电力绩效。
     */
    private String source;
}
