package com.ra.rabnbserver.dto.adminMinerAction;

import lombok.Data;

@Data
public class FragmentExchangeNftDTO {
    /**
     * 卡牌ID（1-铜/2-银/3-金）
     * 合约升级为多卡牌ID，碎片兑换需指定卡牌类型
     */
    private Integer cardId;
    /** 想要兑换的卡牌数量 */
    private Integer quantity;
}
