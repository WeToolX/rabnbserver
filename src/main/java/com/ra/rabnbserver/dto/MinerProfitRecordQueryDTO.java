package com.ra.rabnbserver.dto;


import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * 矿机收益记录查询参数
 */
public class MinerProfitRecordQueryDTO {

    /**
     * (value = "当前页码", example = "1")
     */
    private Integer pageNo = 1;

    /**
     * "每页条数", example = "10"
     */
    private Integer pageSize = 10;

    /**
     * (value = "用户ID", example = "1024")
     */
    private Long userId;

    /**
     * (value = "用户钱包地址", example = "0x71C...3d")
     */
    private String walletAddress;

    /**
     * (value = "矿机类型", example = "0，1，2，3")
     */
    private String minerType;

    /**
     * (value = "矿机id", example = "0，1，2，3")
     */
    private String minerId;

    /**
     * (value = "合约分发状态 (0:未发放, 1:已发放)", example = "1")
     */
    private Integer payoutStatus;

    /**
     * (value = "锁仓类型 (0:直接分发, 1:L1, 2:L2, 3:L3)", example = "0")
     */
    private Integer lockType;

    /**
     * (value = "分发类型 (1:入仓, 2:直接分发)", example = "2")
     */
    private Integer distType;

    /**
     * (value = "开始时间 (支持各种格式: yyyy-MM-dd, yyyy/MM/dd HH:mm:ss 等)", example = "2023-10-01 00:00:00")
     */
    private String startTime;

    /**
     * (value = "结束时间 (支持各种格式: yyyy-MM-dd, yyyy/MM/dd HH:mm:ss 等)", example = "2023-10-31")
     */
    private String endTime;

    /**
     * (value = "交易哈希", example = "0xabc123...")
     */
    private String txId;
}