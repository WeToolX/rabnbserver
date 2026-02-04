package com.ra.rabnbserver.dto;


import lombok.Data;

/**
 * 矿机列表查询请求
 */
@Data
public class MinerQueryDTO {
    private Integer page = 1;
    private Integer size = 10;

    // 以下为筛选条件（可选）
    /**
     * // 矿机类型
     */
    private String minerType;
    /**
     * // 激活状态 (0:待激活, 1:已激活)
     */
    private Integer status;
    /**
     * // 是否已交电费 (0:否, 1:是)
     */
    private Integer isElectricityPaid;
    /**
     * // 是否已加速 (0:否, 1:是)
     */
    private Integer isAccelerated;
    /**
     * // 矿机ID (如 M001)
     */
    private String minerId;

    // 时间范围筛选（针对矿机创建时间）
    private String startTime;
    private String endTime;
}