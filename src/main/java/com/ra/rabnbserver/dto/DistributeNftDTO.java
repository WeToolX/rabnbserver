package com.ra.rabnbserver.dto;

import lombok.Data;

// DTO: 分发NFT请求
@Data
public class DistributeNftDTO {
    private Long userId; // 用户ID
    /**
     * 卡牌ID（1-铜/2-银/3-金）
     * 合约升级为多卡牌ID，分发时必须指定卡牌类型
     */
    private Integer cardId;
    private Integer amount; // 分发数量
}
