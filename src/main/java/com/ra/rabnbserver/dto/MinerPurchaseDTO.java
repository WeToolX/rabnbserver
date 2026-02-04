package com.ra.rabnbserver.dto;

import lombok.Data;

// 购买矿机请求
@Data
public class MinerPurchaseDTO {
    /**
     * 矿机类型
     */
    private String minerType;
    /**
     * 购买数量
     */
    private Integer quantity;
}