package com.ra.rabnbserver.VO.team;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 团队区域列表项视图对象
 */
@Data
public class TeamAreaItemVO {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 地址（通常指钱包地址或位置信息）
     */
    private String address;

    /**
     * 团队总人数
     */
    private Integer teamCount;

    /**
     * 已购买人数/认购人数
     */
    private Integer purchasedCount;

    /**
     * 活跃人数
     */
    private Integer activeCount;

    /**
     * 最近一次兑换时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastExchangeTime;
}