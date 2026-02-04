package com.ra.rabnbserver.dto;

import lombok.Data;

@Data
public class MinerAccelerationDTO {
    /**
     * 加速模式：
     * 1 - 按矿机类型加速（全部处于等待期的该类型矿机）
     * 2 - 按矿机类型+数量加速（处于等待期的该类型矿机，指定数量）
     * 3 - 全部加速（所有处于等待期的矿机）
     * 4 - 单台加速（指定 userMinerId）
     */
    private Integer mode;
    /**
     * 模式 1, 2 必填 矿机类型
     */
    private String minerType;
    /**
     * 模式 2 必填 矿机数量
     */
    private Integer quantity;
    /**
     * 模式 4 必填 矿机id
     */
    private Long userMinerId;
}
