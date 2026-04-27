package com.ra.rabnbserver.VO.gold;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class GoldQuantCommissionSettingsVO {
    private List<RewardLevelRule> rewardLevels = new ArrayList<>();
    private List<RewardGenerationRule> rewardRules = new ArrayList<>();
    private List<DistributionLevelRule> distributionLevels = new ArrayList<>();
    private Integer distributionMaxGeneration = 15;

    @Data
    public static class RewardLevelRule {
        private Integer level;
        private Integer directValidBuyerCount;
    }

    @Data
    public static class RewardGenerationRule {
        private Integer level;
        private Integer minGeneration;
        private Integer maxGeneration;
        private BigDecimal ratio;
    }

    @Data
    public static class DistributionLevelRule {
        private Integer level;
        private Integer teamValidWindowCount;
        private BigDecimal ratio;
    }
}
