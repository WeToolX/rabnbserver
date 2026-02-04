package com.ra.rabnbserver.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
public class MinerSettings {
    private String profitTime = "00:00:00"; // 每日收益执行时间
    private BigDecimal electricFee = new BigDecimal("10.00"); // 电费
    private BigDecimal accelerationFee = new BigDecimal("50.00"); // 加速包价格
    private Map<Integer, BigDecimal> distributionRatios = new HashMap<>(); // 分销比例
}