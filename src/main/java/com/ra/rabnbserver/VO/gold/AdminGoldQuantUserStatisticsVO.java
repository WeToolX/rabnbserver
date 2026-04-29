package com.ra.rabnbserver.VO.gold;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminGoldQuantUserStatisticsVO {
    private Long userId;
    private String walletAddress;
    private Integer purchasedCount = 0;
    private Integer activeCount = 0;
    private Integer windowCount = 0;
    private Integer rewardLevel = 0;
    private Integer distributionLevel = 0;
    private BigDecimal rewardDistributedAmount = BigDecimal.ZERO;
    private BigDecimal distributionDistributedAmount = BigDecimal.ZERO;
}
