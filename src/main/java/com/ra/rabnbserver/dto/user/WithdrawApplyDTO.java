package com.ra.rabnbserver.dto.user;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawApplyDTO {
    /**
     * 提现金额
     */
    private BigDecimal amount;
}