package com.ra.rabnbserver.dto.gold;

import lombok.Data;

@Data
public class AdminGoldQuantUserStatisticsQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String walletAddress;
    private String startTime;
    private String endTime;
    private Integer rewardLevel;
    private Integer distributionLevel;
}
