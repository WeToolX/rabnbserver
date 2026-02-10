package com.ra.rabnbserver.dto;

import lombok.Data;

import java.util.List;

// 交电费请求
@Data
public class MinerElectricityDTO {
    /**
     * 交费模式：
     * 1 - 待激活交费（根据类型）
     * 2 - 即将到期交费（根据天数、数量、类型）
     * 3 - 已到期交费（根据类型）
     * 4 - 全部已到期交费
     * 5 - 指定矿机ID交费
     * 6 - 一键缴纳所有矿机电费（所有已销毁卡牌的矿机）
     */
    private Integer mode;
    /**
     * 矿机类型：// 模式 1, 2, 3 必填
     */
    private String minerType;
    /**
     * // 模式 2 必填（如剩余3天到期的）剩余到期天数
     */
    private Integer days;
    /**
     * // 模式 2 选填（交多少台的电费）
     */
    private Integer quantity;

    /**
     * 模式 5 必填：指定矿机的数据库主键 ID 列表
     */
    private List<Long> userMinerIds;
}