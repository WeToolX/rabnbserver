package com.ra.rabnbserver.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class MinerSettings {
    private String profitTime = "23:59:00"; // 每日收益执行时间
    private String electricityRewardTime =  "23:50:00"; //每日电费分成时间
    private BigDecimal electricFee = new BigDecimal("10.00"); // 电费
    private BigDecimal accelerationFee = new BigDecimal("50.00"); // 加速包价格
    private Map<Integer, BigDecimal> distributionRatios = new HashMap<>(); // 分销比例
    private List<RewardTier> tiers; // 阶梯比例
    private BigDecimal fragmentToCardRate = new BigDecimal("100"); //多少张碎片兑换一张卡牌

    @Data
    public static class RewardTier {
        /** 组织矿机总数(直属下级)达到该值 */
        private Integer minCount;
        /** 对应的比例，如 0.15 代表 15% */
        private BigDecimal ratio;
    }
}