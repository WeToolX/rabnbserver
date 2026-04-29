package com.ra.rabnbserver.dto.miner;

import lombok.Data;

@Data
public class ElectricityRewardRecordQueryDTO {
    private Integer pageNo = 1;
    private Integer pageSize = 10;
    private String startTime;
    private String endTime;
}
