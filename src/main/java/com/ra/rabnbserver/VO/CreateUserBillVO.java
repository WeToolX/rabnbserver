package com.ra.rabnbserver.VO;

import lombok.Data;

@Data
public class CreateUserBillVO {
    private String num;
    /**
     * 卡牌ID（1-铜/2-银/3-金）
     * 合约升级为多卡牌ID，用于账单记录具体卡牌类型
     */
    private Integer cardId;
}
