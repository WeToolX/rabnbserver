package com.ra.rabnbserver.VO;

import lombok.Data;

import java.math.BigDecimal;
//合约基础信息
@Data
public class PaymentUsdtMetaVO {
    private String contractAddress;
    private String usdtAddress;
    private String adminAddress;
    private String executorAddress;
    private String treasuryAddress;
    private BigDecimal minAmount;
}
