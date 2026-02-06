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
}
