package com.ra.rabnbserver.VO;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class WithdrawSettingsVO {
    /**
     * 最小提现金额
     */
    private BigDecimal minAmount;

    /**
     * 手续费率（小数，例如 0.05 代表 5%）
     */
    private BigDecimal feeRate;
}