package com.ra.rabnbserver.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
public class MinerSettings {
    // 收益发放时间 (HH:mm:ss)
    private String profitTime = "01:00:00";
    // 电费金额
    private BigDecimal electricFee = new BigDecimal("10.00");
    // 分销比例 {1: 0.1, 2: 0.05...}
    private Map<Integer, BigDecimal> distributionRatios = new HashMap<>();
    // 应用启动后延时重试开启时间
    private Integer startupDelayMinutes = 3;
}