package com.ra.rabnbserver.VO.gold;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoldQuantTeamSummaryVO {
    private Integer selfValidWindowCount;
    private Integer teamValidWindowCount;
    private Integer bigAreaValidWindowCount;
    private Integer smallAreaValidWindowCount;
    private Integer directValidBuyerCount;
    private Integer rewardLevel;
    private Integer distributionLevel;
    private String rewardGenerationRange;
    private BigDecimal distributionRatio;
    private BigDecimal rewardDistributedAmount;
    private BigDecimal distributionDistributedAmount;
    private BigDecimal totalDistributedAmount;
}
