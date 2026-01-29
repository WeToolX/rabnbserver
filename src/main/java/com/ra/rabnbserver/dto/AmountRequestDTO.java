package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class AmountRequestDTO {
    private String amount; // 操作金额
    private String remark;     // 可选备注
}
