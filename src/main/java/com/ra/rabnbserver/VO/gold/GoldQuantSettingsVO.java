package com.ra.rabnbserver.VO.gold;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GoldQuantSettingsVO {
    private BigDecimal hostingFee = new BigDecimal("40");
    private BigDecimal windowMaintenanceFee = new BigDecimal("200");
    private Integer hostingDays = 30;
    private Integer maintenanceDays = 30;
    private Integer minerThreshold = 10;
    private Integer maxWindowCount = 10;
}
