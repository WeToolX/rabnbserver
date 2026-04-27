package com.ra.rabnbserver.VO.gold;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ra.rabnbserver.enums.GoldQuantCommissionType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class GoldQuantCommissionRecordVO {
    private Long id;
    private Long beneficiaryUserId;
    private String beneficiaryWalletAddress;
    private Long sourceUserId;
    private String sourceWalletAddress;
    private String sourceOrderId;
    private Long sourceBillId;
    private Long commissionBillId;
    private GoldQuantCommissionType commissionType;
    private Integer level;
    private Integer generation;
    private BigDecimal ratio;
    private BigDecimal orderAmount;
    private BigDecimal commissionAmount;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
