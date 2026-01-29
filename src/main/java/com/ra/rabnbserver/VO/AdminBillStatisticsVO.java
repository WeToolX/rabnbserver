package com.ra.rabnbserver.VO;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class AdminBillStatisticsVO {
    /**
     * 所有用户在平台中的总余额 (来自 User 表)
     */
    private BigDecimal totalUserBalance = BigDecimal.ZERO;

    /**
     * 平台累计总充值金额 (来自 UserBill 表，类型为 PLATFORM 且为 DEPOSIT)
     */
    private BigDecimal totalPlatformDeposit = BigDecimal.ZERO;

    private Integer totalNftSalesCount = 0;                     // NFT销售总数 (从备注解析)
    private BigDecimal totalNftPurchaseAmount = BigDecimal.ZERO; // NFT购买总金额
}