package com.ra.rabnbserver.dto.gold;

import com.ra.rabnbserver.enums.GoldQuantCommissionType;
import lombok.Data;

@Data
public class GoldQuantCommissionQueryDTO {
    private GoldQuantCommissionType commissionType;
    private String startTime;
    private String endTime;
    private Integer page = 1;
    private Integer size = 10;
}
