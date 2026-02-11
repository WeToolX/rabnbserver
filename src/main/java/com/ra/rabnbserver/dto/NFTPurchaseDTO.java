package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class NFTPurchaseDTO {
    /**
     * 卡牌ID（1-铜/2-银/3-金）
     * 合约升级为多卡牌ID，必须指定要购买的卡牌类型
     */
    private Integer cardId;
    private String number;
}
