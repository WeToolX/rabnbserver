package com.ra.rabnbserver.dto;

import lombok.Data;

/**
 * AIR 闪兑请求参数
 */
@Data
public class AirFlashSwapDTO {

    /**
     * 用户要闪兑的 AIR 数量
     */
    private String amount;
}
