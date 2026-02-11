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
     * 卡牌ID（1-铜/2-银/3-金）
     * 合约升级为多卡牌ID，购买矿机需明确指定销毁的卡牌类型
     */
    private Integer cardId;
    /**
     * 购买数量
     */
    private Integer quantity;
}
