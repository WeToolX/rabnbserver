package com.ra.rabnbserver.dto.user;

import lombok.Data;

@Data
public class WithdrawAuditDTO {
    /**
     * 提现记录id
     */
    private Long recordId;
    /**
     * true: 通过, false: 驳回
     */
    private Boolean isPass;
    /**
     * 驳回原因等备注
     */
    private String remark;
}