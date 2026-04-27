package com.ra.rabnbserver.dto.gold;

import com.ra.rabnbserver.enums.GoldQuantCommissionType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdminGoldQuantCommissionQueryDTO {
    private Long beneficiaryUserId;
    private String beneficiaryWalletAddress;
    private Long sourceUserId;
    private String sourceWalletAddress;
    private String sourceOrderId;
    private GoldQuantCommissionType commissionType;
    private Integer level;
    private Integer minGeneration;
    private Integer maxGeneration;
    private BigDecimal minRatio;
    private BigDecimal maxRatio;
    private BigDecimal minOrderAmount;
    private BigDecimal maxOrderAmount;
    private BigDecimal minCommissionAmount;
    private BigDecimal maxCommissionAmount;
    private String startTime;
    private String endTime;
    private Integer page = 1;
    private Integer size = 10;
}
