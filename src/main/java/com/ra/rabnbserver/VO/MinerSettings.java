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
    private Map<Integer, BigDecimal> distributionRatios = new HashMap<>(); // distribution ratios
    private List<RewardTier> tiers; // reward tiers
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

    @Data
    public static class RewardTier {
        /** minimum direct miner count */
        private Integer minCount;
        /** ratio such as 0.15 for 15% */
        private BigDecimal ratio;
    }
}
