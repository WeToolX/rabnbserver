package com.ra.rabnbserver.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class MinerSettings {
    private String profitTime = "23:59:00"; // daily profit settlement time
    private String electricityRewardTime = "23:50:00"; // daily electricity reward time
    private BigDecimal electricFee = new BigDecimal("10.00"); // electricity fee
    private BigDecimal accelerationFee = new BigDecimal("50.00"); // acceleration pack fee
    private Boolean activeMinerGradeMode = true; // true: active miners, false: redeemed miners
    private Boolean smallAreaUnlimitedElectricityReward = false; // true: reward all small-area electricity without generation limits
    private Boolean electricityRewardEnabled = false; // miner electricity reward commission switch
    private Map<Integer, BigDecimal> distributionRatios = new HashMap<>(); // distribution ratios
    private List<RewardTier> tiers; // reward tiers
    private List<ElectricityGenerationPerformanceRatio> electricityGenerationPerformanceRatios; // generation performance ratios
    private Map<String, BigDecimal> minerDailyProfits = defaultMinerDailyProfits(); // daily profit per miner type
    private Map<Integer, BigDecimal> fragmentToCardRates = new HashMap<>(); // fragment cost per cardId
    private BigDecimal fragmentToCardRate = new BigDecimal("100"); // legacy fallback rate

    public BigDecimal getFragmentToCardRateByCardId(Integer cardId) {
        if (cardId != null && fragmentToCardRates != null) {
            BigDecimal rate = fragmentToCardRates.get(cardId);
            if (rate != null) {
                return rate;
            }
        }
        return fragmentToCardRate;
    }

    public BigDecimal getMinerDailyProfitByType(String minerType) {
        if (minerDailyProfits == null) {
            minerDailyProfits = defaultMinerDailyProfits();
        }
        BigDecimal amount = minerDailyProfits.get(minerType);
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private static Map<String, BigDecimal> defaultMinerDailyProfits() {
        Map<String, BigDecimal> profits = new HashMap<>();
        profits.put("0", BigDecimal.ZERO);
        profits.put("1", BigDecimal.ZERO);
        profits.put("2", BigDecimal.ZERO);
        profits.put("3", BigDecimal.ZERO);
        return profits;
    }

    @Data
    public static class RewardTier {
        /** user grade */
        private Integer grade;
        /** minimum team active miner count */
        private Integer minCount;
        /** legacy ratio such as 0.15 for 15% */
        private BigDecimal ratio;
        /** final electricity reward ratio such as 0.15 for 15% */
        private BigDecimal rewardRatio;
    }

    @Data
    public static class ElectricityGenerationPerformanceRatio {
        /** 1 means direct subordinate generation */
        private Integer generation;
        /** performance ratio such as 0.70 for 70% */
        private BigDecimal performanceRatio;
    }
}
