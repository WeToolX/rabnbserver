package com.ra.rabnbserver.VO.gold;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoldQuantCommissionStatisticsVO {
    private BigDecimal rewardAmount = BigDecimal.ZERO;
    private BigDecimal distributionAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private Long totalCount = 0L;
    private BigDecimal todayAmount = BigDecimal.ZERO;
}
