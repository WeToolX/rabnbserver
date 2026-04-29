package com.ra.rabnbserver.dto.miner;

import lombok.Data;

@Data
public class AdminMinerUserStatisticsQueryDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String walletAddress;
    private String startTime;
    private String endTime;
    private Integer userGrade;
}
