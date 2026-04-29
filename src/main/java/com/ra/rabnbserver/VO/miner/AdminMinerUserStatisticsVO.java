package com.ra.rabnbserver.VO.miner;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminMinerUserStatisticsVO {
    private Long userId;
    private String walletAddress;
    private Integer purchasedCount = 0;
    private Integer activeCount = 0;
    private Integer userGrade = 0;
    private BigDecimal performanceDistributedAmount = BigDecimal.ZERO;
}
